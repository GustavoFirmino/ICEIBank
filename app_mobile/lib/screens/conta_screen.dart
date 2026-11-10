// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 3 - Partes D e F (saldo, deposito, saque, transferencia, fila offline)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import 'package:flutter/material.dart';

import '../api_client.dart';
import '../app_state.dart';
import '../models.dart';
import '../widgets/leader_panel.dart';

class ContaScreen extends StatefulWidget {
  const ContaScreen({super.key, required this.estado, required this.contaId});
  final AppState estado;
  final int contaId;

  @override
  State<ContaScreen> createState() => _ContaScreenState();
}

class _ContaScreenState extends State<ContaScreen> {
  final _valor = TextEditingController();
  final _destino = TextEditingController();
  final _valorTransf = TextEditingController();
  bool _ocupado = false;

  AppState get estado => widget.estado;

  void _msg(String texto) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(texto)));

  Future<void> _executar(Future<void> Function() acao) async {
    setState(() => _ocupado = true);
    try {
      await acao();
    } on ApiException catch (e) {
      // erro SEMPRE visivel: saldo insuficiente, conta nao encontrada, sessao expirada (401)...
      _msg(e.naoAutorizado ? 'Sessão expirada. Entre novamente.' : e.mensagem);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  Future<void> _operar(TipoOperacao tipo) async {
    final valor = int.tryParse(_valor.text.trim());
    if (valor == null || valor <= 0) {
      _msg('Digite um valor inteiro maior que zero.');
      return;
    }
    await _executar(() async {
      final r = await estado.operar(widget.contaId, tipo, valor);
      _valor.clear();
      _msg(r == ResultadoOperacao.aplicada
          ? '${tipo.rotulo} de R\$ $valor realizado.'
          : '${tipo.rotulo} de R\$ $valor guardado: será sincronizado quando a agência responder.');
    });
  }

  Future<void> _transferir() async {
    final destino = int.tryParse(_destino.text.trim());
    final valor = int.tryParse(_valorTransf.text.trim());
    if (destino == null || valor == null || valor <= 0) {
      _msg('Informe a conta de destino e um valor maior que zero.');
      return;
    }
    await _executar(() async {
      final mensagem = await estado.transferir(widget.contaId, destino, valor);
      _destino.clear();
      _valorTransf.clear();
      _msg(mensagem);
    });
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: estado,
      builder: (context, _) {
        final conta = estado.conta(widget.contaId);
        final ops = estado.pendentes(widget.contaId);
        final offline = estado.contasOffline.contains(widget.contaId);
        final temPendencia = estado.temPendencias(widget.contaId);
        return Scaffold(
          appBar: AppBar(title: Text('Conta ${widget.contaId}'), actions: [
            IconButton(tooltip: 'Atualizar', icon: const Icon(Icons.refresh), onPressed: () => _executar(() => estado.atualizarConta(widget.contaId))),
          ]),
          body: RefreshIndicator(
            onRefresh: () async => _executar(() => estado.atualizarConta(widget.contaId)),
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                LeaderPanel(leader: estado.leader),
                const SizedBox(height: 12),
                if (offline)
                  Card(
                    key: const Key('banner-offline'),
                    color: Theme.of(context).colorScheme.surfaceContainerHighest,
                    child: ListTile(
                      leading: const Icon(Icons.cloud_off),
                      title: const Text('Modo offline'),
                      subtitle: Text('Sem conexão com a Agência ${agenciaDaContaLabel(widget.contaId)}. Mostrando o último saldo conhecido; '
                          'depósitos e saques ficam na fila e sincronizam sozinhos.'),
                    ),
                  ),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(conta?.titular ?? '', style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 4),
                      Text('R\$ ${estado.saldoProvisorio(widget.contaId)}', key: const Key('saldo'), style: Theme.of(context).textTheme.displaySmall),
                      if (temPendencia)
                        Text('Saldo provisório — há operações pendentes de sincronização',
                            key: const Key('saldo-provisorio'), style: TextStyle(color: Theme.of(context).colorScheme.tertiary)),
                      if (conta != null) Text('Último saldo confirmado pela agência: R\$ ${conta.saldo}', style: Theme.of(context).textTheme.bodySmall),
                    ]),
                  ),
                ),
                for (final op in ops) _OperacaoCard(estado: estado, op: op),
                const SizedBox(height: 12),
                Text('Depósito / saque', style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                TextField(
                  key: const Key('campo-valor'),
                  controller: _valor,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Valor (R\$)', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 8),
                Row(children: [
                  Expanded(child: FilledButton(key: const Key('btn-depositar'), onPressed: _ocupado ? null : () => _operar(TipoOperacao.deposito), child: const Text('Depositar'))),
                  const SizedBox(width: 8),
                  Expanded(child: OutlinedButton(key: const Key('btn-sacar'), onPressed: _ocupado ? null : () => _operar(TipoOperacao.saque), child: const Text('Sacar'))),
                ]),
                const SizedBox(height: 24),
                Text('Transferência (exige conexão)', style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                Row(children: [
                  Expanded(
                    child: TextField(
                      controller: _destino,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'Conta destino', border: OutlineInputBorder()),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: _valorTransf,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'Valor (R\$)', border: OutlineInputBorder()),
                    ),
                  ),
                ]),
                const SizedBox(height: 8),
                FilledButton.tonal(onPressed: _ocupado ? null : _transferir, child: const Text('Transferir')),
              ],
            ),
          ),
        );
      },
    );
  }

  String agenciaDaContaLabel(int id) => '${id % 3}';
}

/// Uma operacao da fila, com a marca visual do seu estado.
class _OperacaoCard extends StatelessWidget {
  const _OperacaoCard({required this.estado, required this.op});
  final AppState estado;
  final OperacaoPendente op;

  @override
  Widget build(BuildContext context) {
    final cores = Theme.of(context).colorScheme;
    final descricao = '${op.tipo.rotulo} de R\$ ${op.valor}';
    switch (op.estado) {
      case EstadoOperacao.pendente:
        return Card(
          key: Key('op-pendente-${op.id}'),
          child: ListTile(
            leading: const Icon(Icons.schedule),
            title: Text(descricao),
            subtitle: const Text('Pendente de sincronização'),
            trailing: const Chip(label: Text('pendente')),
          ),
        );
      case EstadoOperacao.conflito:
        return Card(
          key: Key('op-conflito-${op.id}'),
          color: cores.errorContainer,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                const Icon(Icons.warning_amber),
                const SizedBox(width: 8),
                Expanded(child: Text('Conflito: $descricao não foi aplicado', style: Theme.of(context).textTheme.titleSmall)),
              ]),
              const SizedBox(height: 8),
              const Text('O estado da conta mudou enquanto o app estava sem conexão, então a agência recusou aplicar esta operação sem você ver o que mudou.'),
              if (op.contaServidor != null) Text('Saldo real agora: R\$ ${op.contaServidor!.saldo}', style: const TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Wrap(spacing: 8, children: [
                FilledButton(onPressed: () => estado.resolverConflito(op, aplicar: true), child: const Text('Aplicar mesmo assim')),
                OutlinedButton(onPressed: () => estado.resolverConflito(op, aplicar: false), child: const Text('Descartar')),
              ]),
            ]),
          ),
        );
      case EstadoOperacao.rejeitada:
        return Card(
          key: Key('op-rejeitada-${op.id}'),
          color: cores.errorContainer,
          child: ListTile(
            leading: const Icon(Icons.error_outline),
            title: Text('$descricao foi recusado'),
            subtitle: Text(op.mensagem ?? 'A agência recusou a operação.'),
            trailing: TextButton(onPressed: () => estado.dispensar(op), child: const Text('OK')),
          ),
        );
    }
  }
}
