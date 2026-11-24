// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 3 - Parte D (App Flutter)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Unico lugar do app que sabe montar uma requisicao para a API (mesmo papel do httpClient.js do
// frontend web do Sprint 1): anexa o JWT quando existe e normaliza TODO erro em uma ApiException
// com o status HTTP e a mensagem do campo "erro" devolvido pela agencia.
import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:http/http.dart' as http;

import 'models.dart';

class ApiException implements Exception {
  ApiException(this.status, this.mensagem, [this.corpo]);

  /// 0 = nao foi possivel falar com a agencia (sem rede, agencia fora do ar, timeout).
  final int status;
  final String mensagem;
  final Map<String, dynamic>? corpo;

  bool get semConexao => status == 0;
  bool get naoAutorizado => status == 401;
  bool get conflito => status == 409;

  @override
  String toString() => 'ApiException($status): $mensagem';
}

class ApiClient {
  ApiClient({http.Client? client, this.timeout = const Duration(seconds: 4)}) : _http = client ?? http.Client();

  final http.Client _http;
  final Duration timeout;

  Future<Map<String, dynamic>?> _enviar(
    String metodo,
    String baseUrl,
    String caminho, {
    String? token,
    Map<String, dynamic>? corpo,
  }) async {
    final requisicao = http.Request(metodo, Uri.parse('$baseUrl$caminho'));
    requisicao.headers['Content-Type'] = 'application/json';
    if (token != null) requisicao.headers['Authorization'] = 'Bearer $token';
    if (corpo != null) requisicao.body = jsonEncode(corpo);

    http.Response resposta;
    try {
      resposta = await http.Response.fromStream(await _http.send(requisicao).timeout(timeout));
    } catch (_) {
      // SocketException, ClientException (web), TimeoutException...: tudo vira "sem conexao"
      throw ApiException(0, 'Sem conexão com a agência.');
    }

    Map<String, dynamic>? json;
    if (resposta.body.isNotEmpty) {
      try {
        final decodificado = jsonDecode(utf8.decode(resposta.bodyBytes));
        if (decodificado is Map<String, dynamic>) json = decodificado;
      } catch (_) {
        json = null;
      }
    }
    if (resposta.statusCode < 200 || resposta.statusCode >= 300) {
      final mensagem = json?['erro'] as String? ?? 'Erro inesperado (HTTP ${resposta.statusCode}).';
      throw ApiException(resposta.statusCode, mensagem, json);
    }
    return json;
  }

  Future<String> login(String baseUrl, String usuario, String senha) async {
    final r = await _enviar('POST', baseUrl, '/auth/login', corpo: {'username': usuario, 'password': senha});
    return r!['token'] as String;
  }

  Future<Conta> consultarConta(String baseUrl, String token, int id) async =>
      Conta.fromJson((await _enviar('GET', baseUrl, '/contas/$id', token: token))!);

  Future<void> depositar(String baseUrl, String token, int id, int valor) =>
      _enviar('POST', baseUrl, '/contas/$id/depositar', token: token, corpo: {'valor': valor});

  Future<void> sacar(String baseUrl, String token, int id, int valor) =>
      _enviar('POST', baseUrl, '/contas/$id/sacar', token: token, corpo: {'valor': valor});

  /// POST /transferencias. O idOperacao (gerado uma vez por clique) torna a chamada idempotente.
  Future<String> transferir(String baseUrl, String token, int origem, int destino, int valor) async {
    final r = await _enviar('POST', baseUrl, '/transferencias',
        token: token,
        corpo: {'idOrigem': origem, 'idDestino': destino, 'valor': valor, 'idOperacao': novoId()});
    return r?['mensagem'] as String? ?? 'Transferência realizada.';
  }

  /// POST /contas/{id}/sincronizar. Lanca ApiException(409) se o vetor da agencia avancou.
  Future<Conta> sincronizar(String baseUrl, String token, int id, TipoOperacao tipo, int valor, List<int> vetor) async =>
      Conta.fromJson((await _enviar('POST', baseUrl, '/contas/$id/sincronizar',
          token: token, corpo: {'tipo': tipo.api, 'valor': valor, 'vetorConhecido': vetor}))!);

  /// GET /bully/lider (rota publica). Devolve o id da lider que ESTA agencia conhece (null = sem lider).
  Future<int?> consultarLider(String baseUrl) async {
    final r = await _enviar('GET', baseUrl, '/bully/lider');
    return (r?['liderAtual'] as num?)?.toInt();
  }

  static final Random _aleatorio = Random.secure();

  /// Identificador unico de operacao (hex de 128 bits) - sem depender de pacote externo.
  static String novoId() => List.generate(16, (_) => _aleatorio.nextInt(256).toRadixString(16).padLeft(2, '0')).join();
}
