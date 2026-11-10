// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 3 - Parte D (painel da lider atual)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import 'package:flutter/material.dart';

import '../leader_service.dart';

/// Painel "Líder atual": reconstroi sozinho a cada rodada do polling (ListenableBuilder), entao uma
/// reeleicao no backend aparece aqui sem a pessoa tocar em nada.
class LeaderPanel extends StatelessWidget {
  const LeaderPanel({super.key, required this.leader});
  final LeaderService leader;

  String _hora(DateTime t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}:${t.second.toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    final cores = Theme.of(context).colorScheme;
    return ListenableBuilder(
      listenable: leader,
      builder: (context, _) {
        late final String titulo;
        late final String detalhe;
        Color fundo = cores.primaryContainer;
        IconData icone = Icons.workspace_premium;
        if (!leader.carregado) {
          titulo = 'Consultando…';
          detalhe = 'Procurando a líder entre as 3 agências';
          icone = Icons.hourglass_top;
        } else if (leader.semResposta) {
          titulo = leader.lider == null ? 'Sem resposta' : 'Agência ${leader.lider} (última conhecida)';
          detalhe = 'Nenhuma das 3 agências respondeu';
          fundo = cores.errorContainer;
          icone = Icons.cloud_off;
        } else if (leader.lider == null) {
          titulo = 'Eleição em andamento';
          detalhe = 'A Agência ${leader.respondidaPor} ainda não conhece uma líder';
          fundo = cores.tertiaryContainer;
          icone = Icons.how_to_vote;
        } else {
          titulo = 'Agência ${leader.lider}';
          detalhe = 'Informado pela Agência ${leader.respondidaPor}'
              '${leader.atualizadoEm == null ? '' : ' às ${_hora(leader.atualizadoEm!)}'}';
        }
        return Card(
          key: const Key('painel-lider'),
          color: fundo,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(children: [
                  Icon(icone),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      const Text('Líder atual'),
                      Text(titulo, key: const Key('texto-lider'), style: Theme.of(context).textTheme.titleLarge),
                      Text(detalhe, style: Theme.of(context).textTheme.bodySmall),
                    ]),
                  ),
                ]),
                if (leader.trocas.isNotEmpty) ...[
                  const Divider(),
                  Text('Últimas trocas de liderança', style: Theme.of(context).textTheme.labelMedium),
                  for (final t in leader.trocas.take(3))
                    Text('${_hora(t.quando)}  ${t.de == null ? 'sem líder' : 'Ag. ${t.de}'} passou para ${t.para == null ? 'sem líder' : 'Ag. ${t.para}'}',
                        style: Theme.of(context).textTheme.bodySmall),
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}
