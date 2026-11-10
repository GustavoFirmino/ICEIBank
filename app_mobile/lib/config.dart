// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 3 - Parte D (App Flutter)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import 'package:flutter/foundation.dart';

/// Uma das 3 agencias conhecidas pelo app. Porta = 4000 + OFFSET (47) + id, igual ao frontend web.
class Agencia {
  const Agencia(this.id, this.baseUrl);
  final int id;
  final String baseUrl;
  String get nome => 'Agência $id';
}

const int totalAgencias = 3;
const int portaBase = 4047;

/// Host das agencias. No emulador Android, "localhost" e o proprio emulador: a maquina de
/// desenvolvimento e 10.0.2.2. Em celular fisico, use a IP da maquina na rede:
///   flutter run --dart-define=API_HOST=192.168.0.10
String hostDasAgencias() {
  const definido = String.fromEnvironment('API_HOST');
  if (definido.isNotEmpty) return definido;
  if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) return '10.0.2.2';
  return 'localhost';
}

List<Agencia> montarAgencias([String? host]) {
  final h = host ?? hostDasAgencias();
  return List.generate(totalAgencias, (i) => Agencia(i, 'http://$h:${portaBase + i}'));
}

/// Cada conta pertence a UMA agencia (particionamento do Sprint 1): id % 3.
int agenciaDaConta(int contaId) => contaId % totalAgencias;
