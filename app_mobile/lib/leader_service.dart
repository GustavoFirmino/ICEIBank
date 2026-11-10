// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 3 - Parte D (painel de lideranca)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import 'dart:async';

import 'package:flutter/foundation.dart';

import 'api_client.dart';
import 'config.dart';

/// Uma troca de lideranca observada pelo app (funcionalidade adicional: historico de lideres na tela).
class TrocaDeLider {
  TrocaDeLider(this.quando, this.de, this.para);
  final DateTime quando;
  final int? de;
  final int? para;
}

/// Consulta GET /bully/lider periodicamente (polling) para o painel da lider atual.
///
/// - Pergunta a UMA agencia por vez; se ela nao responder (fora do ar), tenta a proxima das 3
///   antes de desistir. Comeca sempre pela ultima que respondeu, para nao gastar o timeout da
///   agencia caida a cada rodada.
/// - Nao ha empilhamento: se uma rodada ainda esta em andamento quando o Timer dispara, a nova e
///   ignorada.
class LeaderService extends ChangeNotifier {
  LeaderService(this._api, this._agencias, {this.intervalo = const Duration(seconds: 3)});

  final ApiClient _api;
  final List<Agencia> _agencias;
  final Duration intervalo;

  Timer? _timer;
  bool _emAndamento = false;
  int _proxima = 0;

  /// Lider segundo a agencia que respondeu (null = ela ainda nao conhece uma lider: eleicao em curso).
  int? lider;

  /// Agencia que respondeu a ultima consulta.
  int? respondidaPor;
  DateTime? atualizadoEm;

  /// true = nenhuma das 3 agencias respondeu na ultima rodada.
  bool semResposta = false;

  /// Ja houve ao menos uma rodada concluida.
  bool carregado = false;

  final List<TrocaDeLider> trocas = [];

  void iniciar() {
    _timer?.cancel();
    atualizar();
    _timer = Timer.periodic(intervalo, (_) => atualizar());
  }

  void parar() {
    _timer?.cancel();
    _timer = null;
  }

  Future<void> atualizar() async {
    if (_emAndamento) return;
    _emAndamento = true;
    try {
      for (var tentativa = 0; tentativa < _agencias.length; tentativa++) {
        final agencia = _agencias[(_proxima + tentativa) % _agencias.length];
        try {
          final novoLider = await _api.consultarLider(agencia.baseUrl);
          _proxima = agencia.id;
          _registrar(novoLider, agencia.id);
          return;
        } on ApiException {
          continue; // tenta a proxima agencia
        }
      }
      semResposta = true;
      carregado = true;
      notifyListeners();
    } finally {
      _emAndamento = false;
    }
  }

  void _registrar(int? novoLider, int agenciaQueRespondeu) {
    // Troca = a lider que o painel mostrava (a ultima conhecida, mesmo que tenha havido uma rodada sem
    // resposta no meio) e diferente da que acabou de ser informada. A primeira leitura nao e troca.
    if (carregado && novoLider != lider) {
      trocas.insert(0, TrocaDeLider(DateTime.now(), lider, novoLider));
      if (trocas.length > 5) trocas.removeLast();
    }
    lider = novoLider;
    respondidaPor = agenciaQueRespondeu;
    atualizadoEm = DateTime.now();
    semResposta = false;
    carregado = true;
    notifyListeners();
  }

  @override
  void dispose() {
    parar();
    super.dispose();
  }
}
