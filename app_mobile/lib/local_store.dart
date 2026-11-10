// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 3 - Parte F (armazenamento local offline-first)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import 'dart:convert';

import 'package:hive_flutter/hive_flutter.dart';

import 'models.dart';

/// Tudo que o app precisa lembrar entre execucoes e sem rede: cache de contas (saldo + vetorAtual),
/// fila de operacoes pendentes e a sessao. Interface separada da implementacao para os testes
/// usarem a versao em memoria.
abstract class LocalStore {
  Conta? conta(int id);
  List<Conta> contas();
  Future<void> salvarConta(Conta conta);

  List<OperacaoPendente> pendentes();
  Future<void> salvarPendentes(List<OperacaoPendente> lista);

  String? get token;
  String? get usuario;
  int? get agenciaDeEntrada;
  Future<void> salvarSessao(String token, String usuario, int agenciaDeEntrada);

  /// Apaga so o token (a sessao expirou): cache e fila de pendentes ficam.
  Future<void> limparToken();
}

class MemoryLocalStore implements LocalStore {
  final Map<int, Conta> _contas = {};
  List<OperacaoPendente> _pendentes = [];
  String? _token;
  String? _usuario;
  int? _agencia;

  @override
  Conta? conta(int id) => _contas[id];
  @override
  List<Conta> contas() => _contas.values.toList()..sort((a, b) => a.id.compareTo(b.id));
  @override
  Future<void> salvarConta(Conta conta) async => _contas[conta.id] = conta;

  @override
  List<OperacaoPendente> pendentes() => List.of(_pendentes);
  @override
  Future<void> salvarPendentes(List<OperacaoPendente> lista) async => _pendentes = List.of(lista);

  @override
  String? get token => _token;
  @override
  String? get usuario => _usuario;
  @override
  int? get agenciaDeEntrada => _agencia;
  @override
  Future<void> salvarSessao(String token, String usuario, int agenciaDeEntrada) async {
    _token = token;
    _usuario = usuario;
    _agencia = agenciaDeEntrada;
  }

  @override
  Future<void> limparToken() async => _token = null;
}

/// Implementacao com Hive (banco NoSQL local; funciona em Android, iOS e web). Cada valor e guardado
/// como texto JSON, o que dispensa gerar adaptadores de tipo.
class HiveLocalStore implements LocalStore {
  HiveLocalStore._(this._box);
  final Box<String> _box;

  static Future<HiveLocalStore> abrir() async {
    await Hive.initFlutter();
    return HiveLocalStore._(await Hive.openBox<String>('iceibank'));
  }

  @override
  Conta? conta(int id) {
    final texto = _box.get('conta:$id');
    return texto == null ? null : Conta.fromJson(jsonDecode(texto) as Map<String, dynamic>);
  }

  @override
  List<Conta> contas() {
    final lista = _box.keys
        .whereType<String>()
        .where((k) => k.startsWith('conta:'))
        .map((k) => Conta.fromJson(jsonDecode(_box.get(k)!) as Map<String, dynamic>))
        .toList();
    lista.sort((a, b) => a.id.compareTo(b.id));
    return lista;
  }

  @override
  Future<void> salvarConta(Conta conta) => _box.put('conta:${conta.id}', jsonEncode(conta.toJson()));

  @override
  List<OperacaoPendente> pendentes() {
    final texto = _box.get('pendentes');
    if (texto == null) return [];
    return (jsonDecode(texto) as List).map((e) => OperacaoPendente.fromJson((e as Map).cast<String, dynamic>())).toList();
  }

  @override
  Future<void> salvarPendentes(List<OperacaoPendente> lista) =>
      _box.put('pendentes', jsonEncode(lista.map((o) => o.toJson()).toList()));

  @override
  String? get token => _box.get('token');
  @override
  String? get usuario => _box.get('usuario');
  @override
  int? get agenciaDeEntrada => int.tryParse(_box.get('agencia') ?? '');

  @override
  Future<void> salvarSessao(String token, String usuario, int agenciaDeEntrada) async {
    await _box.put('token', token);
    await _box.put('usuario', usuario);
    await _box.put('agencia', '$agenciaDeEntrada');
  }

  @override
  Future<void> limparToken() => _box.delete('token');
}
