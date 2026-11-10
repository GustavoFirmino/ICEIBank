// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 3 - Partes D e F (tela principal)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import 'package:flutter/material.dart';

import '../api_client.dart';
import '../app_state.dart';
import '../models.dart';
import '../widgets/leader_panel.dart';
import 'conta_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.estado});
  final AppState estado;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _numero = TextEditingController();
  bool _abrindo = false;

  Future<void> _abrir(int id) async {
    final mensagens = ScaffoldMessenger.of(context);
    setState(() => _abrindo = true);
    try {
      await widget.estado.atualizarConta(id);
      if (widget.estado.conta(id) == null) {
        mensagens.showSnackBar(const SnackBar(content: Text('Sem conexão com a agência e esta conta ainda não está salva no aparelho.')));
        return;
      }
      if (!mounted) return;
      _numero.clear();
      await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ContaScreen(estado: widget.estado, contaId: id)));
    } on ApiException catch (e) {
      mensagens.showSnackBar(SnackBar(content: Text(e.mensagem))); // ex.: 404 conta nao encontrada
    } finally {
      if (mounted) setState(() => _abrindo = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final estado = widget.estado;
    return ListenableBuilder(
      listenable: estado,
      builder: (context, _) {
        final contas = estado.contas();
        return Scaffold(
          appBar: AppBar(
            title: const Text('ICEIBank'),
            actions: [
              IconButton(
                tooltip: 'Sair',
                icon: const Icon(Icons.logout),
                onPressed: () => estado.sair(),
              ),
            ],
          ),
          body: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              LeaderPanel(leader: estado.leader),
              const SizedBox(height: 16),
              Text('Olá, ${estado.usuario ?? ''}', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              Row(children: [
                Expanded(
                  child: TextField(
                    key: const Key('campo-conta'),
                    controller: _numero,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Número da conta', border: OutlineInputBorder()),
                    onSubmitted: (v) {
                      final id = int.tryParse(v);
                      if (id != null) _abrir(id);
                    },
                  ),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: _abrindo
                      ? null
                      : () {
                          final id = int.tryParse(_numero.text);
                          if (id == null) {
                            ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Digite o número da conta.')));
                          } else {
                            _abrir(id);
                          }
                        },
                  child: const Text('Abrir'),
                ),
              ]),
              const SizedBox(height: 16),
              Text('Contas neste aparelho', style: Theme.of(context).textTheme.titleSmall),
              if (contas.isEmpty) const Padding(padding: EdgeInsets.all(12), child: Text('Nenhuma conta consultada ainda.')),
              for (final c in contas) _ContaTile(estado: estado, conta: c),
            ],
          ),
        );
      },
    );
  }
}

class _ContaTile extends StatelessWidget {
  const _ContaTile({required this.estado, required this.conta});
  final AppState estado;
  final Conta conta;

  @override
  Widget build(BuildContext context) {
    final ops = estado.pendentes(conta.id);
    final emConflito = ops.any((o) => o.estado == EstadoOperacao.conflito);
    final pendentes = ops.where((o) => o.estado == EstadoOperacao.pendente).length;
    return Card(
      child: ListTile(
        leading: const Icon(Icons.account_balance_wallet),
        title: Text('Conta ${conta.id} — ${conta.titular}'),
        subtitle: Text(emConflito
            ? 'Conflito de sincronização: toque para resolver'
            : pendentes > 0
                ? '$pendentes operação(ões) pendente(s) de sincronização'
                : estado.contasOffline.contains(conta.id)
                    ? 'Offline — último saldo conhecido'
                    : 'Sincronizada'),
        trailing: Text('R\$ ${estado.saldoProvisorio(conta.id)}', style: Theme.of(context).textTheme.titleMedium),
        onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => ContaScreen(estado: estado, contaId: conta.id))),
      ),
    );
  }
}
