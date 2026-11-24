// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 3 - Partes D e F (App Flutter)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import 'dart:async';

import 'package:flutter/material.dart';

import 'api_client.dart';
import 'app_state.dart';
import 'config.dart';
import 'leader_service.dart';
import 'local_store.dart';
import 'screens/home_screen.dart';
import 'screens/login_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final store = await HiveLocalStore.abrir();
  final api = ApiClient();
  final agencias = montarAgencias();
  final estado = AppState(
    api: api,
    store: store,
    agencias: agencias,
    leader: LeaderService(api, agencias),
    conectividade: PlusConnectivityMonitor(),
  )..iniciar();
  runApp(IceiBankApp(estado: estado));
}

class IceiBankApp extends StatelessWidget {
  const IceiBankApp({super.key, required this.estado});
  final AppState estado;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ICEIBank',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: const Color(0xFF0B5FA5), useMaterial3: true),
      darkTheme: ThemeData(colorSchemeSeed: const Color(0xFF0B5FA5), brightness: Brightness.dark, useMaterial3: true),
      home: _Raiz(estado: estado),
    );
  }
}

/// Mostra o login ou a tela principal conforme haja sessao; escuta os avisos (sincronizacao, conflito)
/// e os mostra em SnackBar em qualquer tela.
class _Raiz extends StatefulWidget {
  const _Raiz({required this.estado});
  final AppState estado;

  @override
  State<_Raiz> createState() => _RaizState();
}

class _RaizState extends State<_Raiz> {
  StreamSubscription<String>? _avisos;

  @override
  void initState() {
    super.initState();
    _avisos = widget.estado.avisos.listen((msg) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
    });
  }

  @override
  void dispose() {
    _avisos?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.estado,
      builder: (context, _) =>
          widget.estado.logado ? HomeScreen(estado: widget.estado) : LoginScreen(estado: widget.estado),
    );
  }
}
