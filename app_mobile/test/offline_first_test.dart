// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 3 - Partes D e F (testes do modo offline-first e do painel da lider)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Usa um "servidor falso" que imita as regras do backend (vetor da agencia + 409 na sincronizacao),
// um cache em memoria e uma conectividade controlada pelo teste.
import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:iceibank_mobile/api_client.dart';
import 'package:iceibank_mobile/app_state.dart';
import 'package:iceibank_mobile/config.dart';
import 'package:iceibank_mobile/leader_service.dart';
import 'package:iceibank_mobile/local_store.dart';
import 'package:iceibank_mobile/models.dart';

/// Imita as agencias: cada uma tem um vetor (3 posicoes); toda escrita incrementa a propria posicao.
class AgenciasFalsas {
  bool noAr = true;
  bool tokenValido = true;
  int saldo = 100;
  List<int> vetor = [5, 1, 0]; // agencia 0
  final List<int?> liderPorAgencia = [2, 2, 2];
  final List<bool> agenciaNoAr = [true, true, true];
  final List<String> chamadas = [];

  http.Response _json(int status, Object corpo) =>
      http.Response(jsonEncode(corpo), status, headers: {'content-type': 'application/json'});

  Future<http.Response> tratar(http.Request r) async {
    final porta = r.url.port - portaBase;
    chamadas.add('${r.method} :${r.url.port}${r.url.path}');
    if (!noAr || !agenciaNoAr[porta]) throw http.ClientException('conexao recusada');
    final caminho = r.url.path;
    if (caminho == '/bully/lider') return _json(200, {'idAgencia': porta, 'liderAtual': liderPorAgencia[porta]});
    if (caminho == '/auth/login') return _json(200, {'token': 'abc', 'expiraEmSegundos': 300});
    if (!tokenValido) return _json(401, {'erro': 'Token inválido ou expirado.'});
    final corpo = r.body.isEmpty ? <String, dynamic>{} : jsonDecode(r.body) as Map<String, dynamic>;
    Map<String, dynamic> conta() => {'id': 0, 'titular': 'Gustavo', 'saldo': saldo, 'vetorAtual': vetor};
    if (r.method == 'GET' && caminho == '/contas/0') return _json(200, conta());
    if (caminho == '/contas/0/depositar') {
      saldo += corpo['valor'] as int;
      vetor = [vetor[0] + 1, vetor[1], vetor[2]];
      return _json(200, {'id': 0, 'titular': 'Gustavo', 'saldo': saldo});
    }
    if (caminho == '/contas/0/sincronizar') {
      final conhecido = (corpo['vetorConhecido'] as List).cast<int>();
      if (conhecido.join(',') != vetor.join(',')) {
        return _json(409, {'erro': 'Conflito detectado', 'vetorConhecido': conhecido, 'vetorAtual': vetor, 'contaAtual': conta()});
      }
      final valor = corpo['valor'] as int;
      if (corpo['tipo'] == 'SAQUE') {
        if (saldo < valor) return _json(400, {'erro': 'Saldo insuficiente.'});
        saldo -= valor;
      } else {
        saldo += valor;
      }
      vetor = [vetor[0] + 1, vetor[1], vetor[2]];
      return _json(200, conta());
    }
    return _json(404, {'erro': 'rota inexistente'});
  }
}


class ConectividadeFalsa implements ConnectivityMonitor {
  final _c = StreamController<bool>.broadcast();
  @override
  Stream<bool> get aoMudar => _c.stream;
  void emitir(bool v) => _c.add(v);
}

Future<void> deixarProcessar() => Future<void>.delayed(const Duration(milliseconds: 50));

void main() {
  late AgenciasFalsas servidor;
  late MemoryLocalStore store;
  late ConectividadeFalsa rede;
  late AppState app;

  setUp(() async {
    servidor = AgenciasFalsas();
    store = MemoryLocalStore();
    rede = ConectividadeFalsa();
    final api = ApiClient(
      client: MockClient((r) => servidor.tratar(r)),
      timeout: const Duration(seconds: 1),
    );
    final agencias = montarAgencias('localhost');
    app = AppState(
      api: api,
      store: store,
      agencias: agencias,
      leader: LeaderService(api, agencias, intervalo: const Duration(milliseconds: 30)),
      conectividade: rede,
      intervaloDeReenvio: const Duration(hours: 1), // so a conectividade dispara a sincronizacao nos testes
    );
    app.iniciar();
    await app.login(0, 'gustavo', 'senha123');
  });

  tearDown(() => app.dispose());

  test('offline: deposito vai para a fila com o vetor conhecido e o saldo provisorio ja o inclui', () async {
    await app.atualizarConta(0); // cache: saldo 100, vetor [5,1,0]
    servidor.noAr = false;

    final r = await app.operar(0, TipoOperacao.deposito, 50);

    expect(r, ResultadoOperacao.enfileirada);
    final fila = app.pendentes(0);
    expect(fila, hasLength(1));
    expect(fila.single.vetorConhecido, [5, 1, 0]);
    expect(fila.single.estado, EstadoOperacao.pendente);
    expect(app.saldoProvisorio(0), 150); // UI otimista
    expect(app.conta(0)!.saldo, 100); // o confirmado pela agencia continua 100
    expect(app.contasOffline, contains(0));
  });

  test('ao reconectar a fila sincroniza sozinha, sem acao da pessoa', () async {
    await app.atualizarConta(0);
    servidor.noAr = false;
    await app.operar(0, TipoOperacao.deposito, 50);
    expect(app.pendentes(0), isNotEmpty);

    servidor.noAr = true;
    rede.emitir(true); // evento de conectividade: nenhuma chamada a sincronizar() pelo teste
    await deixarProcessar();

    expect(app.pendentes(0), isEmpty);
    expect(servidor.saldo, 150);
    expect(app.conta(0)!.saldo, 150);
    expect(app.saldoProvisorio(0), 150);
    expect(app.contasOffline, isEmpty);
  });

  test('duas operacoes offline: a segunda encadeia no vetor devolvido pela primeira (sem falso conflito)', () async {
    await app.atualizarConta(0);
    servidor.noAr = false;
    await app.operar(0, TipoOperacao.deposito, 10);
    await app.operar(0, TipoOperacao.saque, 30);
    expect(app.saldoProvisorio(0), 80);

    servidor.noAr = true;
    await app.sincronizar();

    expect(app.pendentes(0), isEmpty);
    expect(servidor.saldo, 80);
  });

  test('conflito (409): a operacao NAO some nem e aplicada; cache mostra o estado real; pessoa decide', () async {
    await app.atualizarConta(0);
    servidor.noAr = false;
    await app.operar(0, TipoOperacao.saque, 30);
    // enquanto o app estava offline, outro caminho mexeu na conta:
    servidor.noAr = true;
    servidor.saldo += 10;
    servidor.vetor = [6, 1, 0];

    await app.sincronizar();

    final op = app.pendentes(0).single;
    expect(op.estado, EstadoOperacao.conflito);
    expect(op.contaServidor!.saldo, 110);
    expect(servidor.saldo, 110, reason: 'o saque nao pode ter sido aplicado');
    expect(app.conta(0)!.saldo, 110, reason: 'o cache passa a refletir o estado real');
    expect(app.saldoProvisorio(0), 110, reason: 'conflito nao conta como pendente no saldo provisorio');

    await app.resolverConflito(op, aplicar: true); // "aplicar mesmo assim" usa o vetor atual do 409
    expect(app.pendentes(0), isEmpty);
    expect(servidor.saldo, 80);
  });

  test('conflito: descartar remove a operacao e nao toca na conta', () async {
    await app.atualizarConta(0);
    servidor.noAr = false;
    await app.operar(0, TipoOperacao.deposito, 5);
    servidor.noAr = true;
    servidor.vetor = [9, 1, 0];
    await app.sincronizar();

    await app.resolverConflito(app.pendentes(0).single, aplicar: false);

    expect(app.pendentes(0), isEmpty);
    expect(servidor.saldo, 100);
  });

  test('saque offline maior que o saldo provisorio e recusado na hora (risco da UI otimista)', () async {
    await app.atualizarConta(0);
    servidor.noAr = false;
    await expectLater(app.operar(0, TipoOperacao.saque, 500), throwsA(isA<ApiException>()));
    expect(app.pendentes(0), isEmpty);
  });

  test('sem nenhum dado local da conta, nao ha como operar offline', () async {
    servidor.noAr = false;
    await expectLater(app.operar(0, TipoOperacao.deposito, 5), throwsA(isA<ApiException>()));
  });

  test('operacao online (sem fila) e aplicada direto e atualiza o vetor do cache', () async {
    await app.atualizarConta(0);
    final r = await app.operar(0, TipoOperacao.deposito, 25);
    expect(r, ResultadoOperacao.aplicada);
    expect(app.conta(0)!.saldo, 125);
    expect(app.conta(0)!.vetorAtual, [6, 1, 0]);
  });

  test('401 na sincronizacao: sessao expira mas a fila fica guardada', () async {
    await app.atualizarConta(0);
    servidor.noAr = false;
    await app.operar(0, TipoOperacao.deposito, 5);
    servidor.noAr = true;
    servidor.tokenValido = false;

    await app.sincronizar();

    expect(app.logado, isFalse);
    expect(app.sessaoExpirada, isTrue);
    expect(app.pendentes(0), hasLength(1));
  });

  group('painel da lider', () {
    test('mostra a lider e detecta a troca depois de uma reeleicao (sem acao do usuario)', () async {
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(app.leader.lider, 2);

      servidor.liderPorAgencia.setAll(0, [1, 1, 1]); // a 2 caiu e a 1 assumiu
      servidor.agenciaNoAr[2] = false;
      await Future<void>.delayed(const Duration(milliseconds: 150));

      expect(app.leader.lider, 1);
      expect(app.leader.trocas.first.de, 2);
      expect(app.leader.trocas.first.para, 1);
    });

    test('se a agencia consultada esta fora do ar, tenta a proxima antes de desistir', () async {
      servidor.agenciaNoAr[0] = false;
      servidor.agenciaNoAr[1] = false;
      await Future<void>.delayed(const Duration(milliseconds: 150));
      expect(app.leader.semResposta, isFalse);
      expect(app.leader.respondidaPor, 2);

      servidor.agenciaNoAr[2] = false;
      await Future<void>.delayed(const Duration(milliseconds: 150));
      expect(app.leader.semResposta, isTrue); // so desiste quando as 3 falham
    });

    test('duas agencias com visoes diferentes durante a transicao: converge na proxima consulta', () async {
      servidor.liderPorAgencia.setAll(0, [2, 1, 1]); // a 0 ainda nao soube da eleicao
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(app.leader.respondidaPor, isNotNull);
      servidor.liderPorAgencia.setAll(0, [1, 1, 1]); // a 0 aprendeu
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(app.leader.lider, 1);
    });
  });
}
