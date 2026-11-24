// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 3 - Parte D (login JWT)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import 'package:flutter/material.dart';

import '../api_client.dart';
import '../app_state.dart';
import '../widgets/leader_panel.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.estado});
  final AppState estado;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _usuario = TextEditingController();
  final _senha = TextEditingController();
  int _agencia = 0;
  bool _carregando = false;
  String? _erro;

  Future<void> _entrar() async {
    setState(() {
      _carregando = true;
      _erro = null;
    });
    try {
      await widget.estado.login(_agencia, _usuario.text.trim(), _senha.text);
    } on ApiException catch (e) {
      // 401 = credenciais erradas; 0 = agencia fora do ar: a mensagem da API/cliente ja diz qual
      if (mounted) setState(() => _erro = e.semConexao ? 'Não foi possível conectar à ${widget.estado.agencias[_agencia].nome}. Ela está no ar?' : e.mensagem);
    } finally {
      if (mounted) setState(() => _carregando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final estado = widget.estado;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('ICEIBank', style: Theme.of(context).textTheme.headlineLarge, textAlign: TextAlign.center),
                  const SizedBox(height: 24),
                  LeaderPanel(leader: estado.leader),
                  const SizedBox(height: 24),
                  if (estado.sessaoExpirada)
                    const Padding(
                      padding: EdgeInsets.only(bottom: 12),
                      child: Text('Sua sessão expirou. Entre novamente — as operações pendentes continuam guardadas e serão sincronizadas.',
                          style: TextStyle(color: Colors.orange)),
                    ),
                  DropdownButtonFormField<int>(
                    initialValue: _agencia,
                    decoration: const InputDecoration(labelText: 'Agência de entrada', border: OutlineInputBorder()),
                    items: [for (final a in estado.agencias) DropdownMenuItem(value: a.id, child: Text(a.nome))],
                    onChanged: (v) => setState(() => _agencia = v ?? 0),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _usuario,
                    decoration: const InputDecoration(labelText: 'Usuário', border: OutlineInputBorder()),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _senha,
                    obscureText: true,
                    decoration: const InputDecoration(labelText: 'Senha', border: OutlineInputBorder()),
                    onSubmitted: (_) => _entrar(),
                  ),
                  if (_erro != null) ...[
                    const SizedBox(height: 12),
                    Text(_erro!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ],
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: _carregando ? null : _entrar,
                    child: _carregando
                        ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Text('Entrar'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
