// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 3 - Partes D e F (estado do app: fluxo bancario + offline-first)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/foundation.dart';

import 'api_client.dart';
import 'config.dart';
import 'leader_service.dart';
import 'local_store.dart';
import 'models.dart';

/// Quando a rede muda. Interface propria para os testes simularem "voltou a conexao".
abstract class ConnectivityMonitor {
  /// true = ha alguma interface de rede ativa.
  Stream<bool> get aoMudar;
}

class PlusConnectivityMonitor implements ConnectivityMonitor {
  @override
  Stream<bool> get aoMudar => Connectivity().onConnectivityChanged.map((r) => r.any((c) => c != ConnectivityResult.none));
}

/// Resultado de um deposito/saque: foi aplicado na agencia agora ou ficou na fila.
enum ResultadoOperacao { aplicada, enfileirada }

/// Estado e regras do app. Estrategia offline-first (Parte F):
///  - leitura: o saldo vem do cache local (Hive) e e atualizado pela agencia quando ela responde;
///  - escrita (deposito/saque): se a agencia nao responde, a operacao vai para uma FILA local junto com o
///    vetorAtual que o app conhecia, e a tela mostra o saldo PROVISORIO (UI otimista) com a marca "pendente";
///  - sincronizacao automatica: ao detectar que a conexao voltou (connectivity_plus) e, como a rede pode
///    estar ligada com a agencia ainda fora do ar, tambem por um reenvio periodico enquanto houver fila;
///  - conflito (HTTP 409): a operacao NAO e aplicada nem descartada em silencio: fica marcada como
///    "conflito", o cache e atualizado com o estado real da conta e a pessoa decide (aplicar mesmo assim
///    ou descartar).
class AppState extends ChangeNotifier {
  AppState({
    required this.api,
    required this.store,
    required this.agencias,
    required this.leader,
    this._conectividade,
    this.intervaloDeReenvio = const Duration(seconds: 5),
  }) {
    _token = store.token;
    usuario = store.usuario;
  }

  final ApiClient api;
  final LocalStore store;
  final List<Agencia> agencias;
  final LeaderService leader;
  final ConnectivityMonitor? _conectividade;
  final Duration intervaloDeReenvio;

  String? _token;
  String? usuario;
  bool sessaoExpirada = false;

  /// Contas cuja ultima tentativa de falar com a agencia falhou por falta de conexao.
  final Set<int> contasOffline = {};

  /// Mensagens curtas para a UI mostrar em SnackBar (ex.: "2 operacoes sincronizadas").
  final StreamController<String> _avisos = StreamController<String>.broadcast();
  Stream<String> get avisos => _avisos.stream;

  StreamSubscription<bool>? _assinatura;
  Timer? _reenvio;
  bool _sincronizando = false;

  bool get logado => _token != null;

  // ---------------------------------------------------------------- sessao

  Future<void> login(int agenciaDeEntrada, String usuarioDigitado, String senha) async {
    final token = await api.login(agencias[agenciaDeEntrada].baseUrl, usuarioDigitado, senha);
    _token = token;
    usuario = usuarioDigitado;
    sessaoExpirada = false;
    await store.salvarSessao(token, usuarioDigitado, agenciaDeEntrada);
    notifyListeners();
    unawaited(sincronizar()); // pode haver fila de uma sessao anterior
  }

  Future<void> sair() async {
    _token = null;
    await store.limparToken();
    notifyListeners();
  }

  /// 401: o JWT expirou. O token e descartado, mas cache e fila ficam; a pessoa entra de novo e a fila sincroniza.
  Future<void> _tokenExpirou() async {
    sessaoExpirada = true;
    await sair();
  }

  // ---------------------------------------------------------------- inicio / fim

  void iniciar() {
    _assinatura = _conectividade?.aoMudar.listen((temRede) {
      if (temRede) unawaited(sincronizar());
    });
    _reenvio = Timer.periodic(intervaloDeReenvio, (_) {
      if (pendentes().any((o) => o.estado == EstadoOperacao.pendente)) unawaited(sincronizar());
    });
    leader.iniciar();
    unawaited(sincronizar());
  }

  @override
  void dispose() {
    _assinatura?.cancel();
    _reenvio?.cancel();
    leader.dispose();
    _avisos.close();
    super.dispose();
  }

  // ---------------------------------------------------------------- leitura (cache primeiro)

  List<Conta> contas() => store.contas();
  Conta? conta(int id) => store.conta(id);
  List<OperacaoPendente> pendentes([int? contaId]) =>
      store.pendentes().where((o) => contaId == null || o.contaId == contaId).toList();

  /// Saldo que a tela mostra: o ultimo saldo conhecido da agencia + o efeito das operacoes ainda pendentes.
  int saldoProvisorio(int contaId) {
    final base = store.conta(contaId)?.saldo ?? 0;
    return base + pendentes(contaId).where((o) => o.estado == EstadoOperacao.pendente).fold(0, (s, o) => s + o.efeito);
  }

  bool temPendencias(int contaId) => pendentes(contaId).any((o) => o.estado == EstadoOperacao.pendente);

  String get _tokenOuErro => _token ?? (throw ApiException(401, 'Sessão expirada. Entre novamente.'));

  /// Atualiza o cache com o que a agencia diz agora. Sem conexao, mantem o cache e marca a conta como offline.
  Future<void> atualizarConta(int id) async {
    try {
      final conta = await api.consultarConta(agencias[agenciaDaConta(id)].baseUrl, _tokenOuErro, id);
      await store.salvarConta(conta);
      contasOffline.remove(id);
    } on ApiException catch (e) {
      if (e.semConexao) {
        contasOffline.add(id);
      } else if (e.naoAutorizado) {
        await _tokenExpirou();
      } else {
        notifyListeners();
        rethrow;
      }
    }
    notifyListeners();
    unawaited(sincronizar());
  }

  // ---------------------------------------------------------------- escrita

  Future<ResultadoOperacao> operar(int contaId, TipoOperacao tipo, int valor) async {
    if (valor <= 0) throw ApiException(400, 'O valor precisa ser maior que zero.');
    final emFila = pendentes(contaId).any((o) => o.estado != EstadoOperacao.rejeitada);
    if (!emFila) {
      try {
        final base = agencias[agenciaDaConta(contaId)].baseUrl;
        if (tipo == TipoOperacao.deposito) {
          await api.depositar(base, _tokenOuErro, contaId, valor);
        } else {
          await api.sacar(base, _tokenOuErro, contaId, valor);
        }
        contasOffline.remove(contaId);
        await atualizarConta(contaId); // traz o novo saldo e o novo vetorAtual
        return ResultadoOperacao.aplicada;
      } on ApiException catch (e) {
        if (e.naoAutorizado) {
          await _tokenExpirou();
          rethrow;
        }
        if (!e.semConexao) rethrow; // 400 (saldo insuficiente) etc.: erro de verdade, mostrado a pessoa
        contasOffline.add(contaId);
      }
    }
    await _enfileirar(contaId, tipo, valor);
    return ResultadoOperacao.enfileirada;
  }

  Future<void> _enfileirar(int contaId, TipoOperacao tipo, int valor) async {
    final cache = store.conta(contaId);
    if (cache == null) {
      throw ApiException(0, 'Sem conexão e esta conta nunca foi consultada: não há dados locais para operar offline.');
    }
    if (tipo == TipoOperacao.saque && valor > saldoProvisorio(contaId)) {
      // risco da UI otimista num app bancario: nao prometer um saque que ja se sabe impossivel
      throw ApiException(400, 'Saldo provisório insuficiente (R\$ ${saldoProvisorio(contaId)}).');
    }
    // Todas as operacoes feitas offline partem do MESMO vetor (o da ultima leitura da agencia). No envio,
    // so a primeira usa esse vetor; as seguintes encadeiam no vetor devolvido pela anterior (ver sincronizar).
    final lista = store.pendentes()
      ..add(OperacaoPendente(
        id: ApiClient.novoId(),
        contaId: contaId,
        tipo: tipo,
        valor: valor,
        vetorConhecido: cache.vetorAtual,
        saldoConhecido: cache.saldo,
        criadaEm: DateTime.now(),
      ));
    await store.salvarPendentes(lista);
    notifyListeners();
  }

  /// Transferencia exige a agencia: o sincronizar do backend so cobre deposito e saque, entao
  /// transferir offline nao e suportado (e a tela diz isso, em vez de enfileirar algo que nao se sincroniza).
  Future<String> transferir(int origem, int destino, int valor) async {
    if (valor <= 0) throw ApiException(400, 'O valor precisa ser maior que zero.');
    try {
      final msg = await api.transferir(agencias[agenciaDaConta(origem)].baseUrl, _tokenOuErro, origem, destino, valor);
      await atualizarConta(origem);
      return msg;
    } on ApiException catch (e) {
      if (e.naoAutorizado) await _tokenExpirou();
      if (e.semConexao) {
        throw ApiException(0, 'Sem conexão com a agência: transferências só podem ser feitas online.');
      }
      rethrow;
    }
  }

  // ---------------------------------------------------------------- sincronizacao

  /// Envia a fila pendente, conta por conta e na ordem em que foi feita. Seguro chamar a qualquer momento:
  /// nao reentra se ja houver uma sincronizacao em curso.
  Future<void> sincronizar() async {
    if (_sincronizando || _token == null) return;
    _sincronizando = true;
    var aplicadas = 0;
    var conflitos = 0;
    try {
      final todas = store.pendentes();
      final contasComFila = todas.where((o) => o.estado == EstadoOperacao.pendente).map((o) => o.contaId).toSet();
      for (final contaId in contasComFila) {
        List<int>? vetorEncadeado;
        int? saldoEncadeado;
        var interromper = false;
        for (final op in todas.where((o) => o.contaId == contaId).toList()) {
          if (interromper) break;
          if (op.estado == EstadoOperacao.conflito) break; // as seguintes dependem dela: esperam a decisao
          if (op.estado != EstadoOperacao.pendente) continue;
          var vetorUsado = vetorEncadeado ?? op.vetorConhecido;
          var retentou = false;
          while (true) {
            try {
              final base = agencias[agenciaDaConta(contaId)].baseUrl;
              final conta = await api.sincronizar(base, _tokenOuErro, contaId, op.tipo, op.valor, vetorUsado);
              await store.salvarConta(conta);
              vetorEncadeado = conta.vetorAtual;
              saldoEncadeado = conta.saldo;
              todas.remove(op);
              contasOffline.remove(contaId);
              aplicadas++;
              break;
            } on ApiException catch (e) {
              if (e.semConexao) {
                contasOffline.add(contaId);
                await store.salvarPendentes(todas);
                notifyListeners();
                return; // tenta de novo na proxima oportunidade
              }
              if (e.naoAutorizado) {
                await store.salvarPendentes(todas);
                await _tokenExpirou();
                return;
              }
              contasOffline.remove(contaId); // a agencia RESPONDEU (so recusou): nao estamos mais offline
              if (e.conflito) {
                final atual = e.corpo?['contaAtual'];
                final real = atual is Map ? Conta.fromJson(atual.cast<String, dynamic>()) : null;
                // "Falso conflito": o vetor da agencia avancou (ex.: eleicao do Bully, operacao de OUTRA conta),
                // mas o saldo desta conta e exatamente o que a pessoa tinha visto ao decidir. Nesse caso a
                // decisao dela continua valida e o app repete UMA vez com o vetor novo, sem incomodar.
                final esperado = saldoEncadeado ?? op.saldoConhecido;
                if (real != null && !retentou && esperado != null && real.saldo == esperado) {
                  retentou = true;
                  vetorUsado = real.vetorAtual;
                  continue;
                }
                op.estado = EstadoOperacao.conflito;
                op.mensagem = e.mensagem;
                if (real != null) {
                  op.contaServidor = real;
                  await store.salvarConta(real); // o cache passa a refletir o estado REAL
                }
                conflitos++;
                interromper = true;
                break;
              }
              op.estado = EstadoOperacao.rejeitada; // ex.: saldo insuficiente de verdade
              op.mensagem = e.mensagem;
              break;
            }
          }
        }
      }
      await store.salvarPendentes(todas);
    } finally {
      _sincronizando = false;
      notifyListeners();
    }
    if (aplicadas > 0) {
      _avisos.add(aplicadas == 1 ? '1 operação sincronizada com a agência.' : '$aplicadas operações sincronizadas com a agência.');
    }
    if (conflitos > 0) {
      _avisos.add('Conflito: uma operação não pôde ser aplicada automaticamente. Veja os detalhes na conta.');
    }
  }

  /// Decisao da pessoa sobre uma operacao em conflito: aplicar mesmo assim (usa o vetor ATUAL devolvido no 409
  /// como novo vetor conhecido, ja que ela viu o estado real) ou descartar.
  Future<void> resolverConflito(OperacaoPendente op, {required bool aplicar}) async {
    final lista = store.pendentes();
    final alvo = lista.firstWhere((o) => o.id == op.id);
    if (aplicar) {
      final real = alvo.contaServidor;
      if (real != null) alvo.vetorConhecido = real.vetorAtual;
      alvo.estado = EstadoOperacao.pendente;
      alvo.mensagem = null;
      alvo.contaServidor = null;
      await store.salvarPendentes(lista);
      notifyListeners();
      await sincronizar();
    } else {
      lista.removeWhere((o) => o.id == op.id);
      await store.salvarPendentes(lista);
      notifyListeners();
    }
  }

  /// Remove da lista uma operacao rejeitada (a pessoa ja leu o motivo).
  Future<void> dispensar(OperacaoPendente op) async {
    final lista = store.pendentes()..removeWhere((o) => o.id == op.id);
    await store.salvarPendentes(lista);
    notifyListeners();
  }
}
