# Respostas — ICEIBank, Sprint 1

**Aluno:** Gustavo Pessoa Firmino Duarte
**Disciplina:** Laboratório de Desenvolvimento de Aplicações Móveis e Distribuídas
**OFFSET pessoal (2 últimos dígitos da matrícula):** 47

> Este documento é preenchido ao longo do sprint, parte por parte (ver README para o índice de partes). As seções abaixo serão completadas conforme cada parte for implementada e testada — não deixadas para o final.

---

## Parte B — Relógio de Lamport

**1. Por que o relógio de Lamport usa `max(contador_local, timestampRecebido) + 1` ao receber uma mensagem, em vez de simplesmente adotar o timestamp recebido diretamente?**

Porque o objetivo do relógio é garantir que o timestamp de qualquer evento seja **maior que o de tudo que já aconteceu antes dele, causalmente** — tanto os eventos que já rodaram localmente nesta agência quanto o evento de origem da mensagem recebida. Se a agência simplesmente adotasse o timestamp recebido, ela poderia "voltar no tempo": se o contador local já estivesse em 10 e chegasse uma mensagem com timestamp 3, adotar 3 diretamente faria com que o próximo evento local recebesse um timestamp menor que eventos que essa mesma agência já processou — quebrando a garantia de que causa sempre tem timestamp menor que consequência. O `max(...)` garante que o novo valor nunca seja menor que o que a agência já tinha visto (nem local, nem remoto); o `+ 1` garante que o evento de recebimento em si seja estritamente posterior à mensagem que o causou.

**2. Se a Agência 0 está no evento de contador 10 e recebe uma mensagem com timestamp 3 (de uma agência mais "atrasada"), qual o novo valor do contador da Agência 0? O que isso implica sobre agências que processam muitos eventos rapidamente versus agências mais lentas?**

O novo valor é **11** — `max(10, 3) + 1 = 11`. Ou seja, o timestamp recebido (3) é simplesmente descartado em favor do contador local, que já estava mais adiantado; só o `+1` é aplicado. Isso foi confirmado no teste automatizado `aoReceberIgnoraOTimestampRecebido_quandoLocalJaEMaior` (`LamportClockServiceTest.java`).

Isso implica que uma agência que processa eventos rapidamente (contador alto) **nunca é "puxada para trás"** por mensagens vindas de agências mais lentas (contador baixo) — o relógio de Lamport é monotonicamente crescente em cada processo, por design. Na prática, isso também significa que o valor absoluto do contador de cada agência tende a refletir o quão "ocupada" ela está: uma agência que recebe muitas transferências de entrada (que disparam `aoReceber`) ou que processa muitos eventos locais avança seu contador mais rápido que uma agência mais ociosa — e, quando as duas trocam mensagens, é sempre a mais adiantada que "define o ritmo" da próxima marcação de tempo, nunca o contrário.

## Parte D — Transferências e limitação conhecida

_A preencher após a implementação da Parte D._

## Parte E — Linha do tempo unificada

_A preencher após a implementação da Parte E._

## Parte F — Autenticação (JWT)

_A preencher após a implementação da Parte F, incluindo a justificativa do modelo de login escolhido e da autenticação entre agências (creditar-remoto)._

## Parte G — Frontend

_A preencher após a implementação da Parte G._

## Funcionalidades adicionais

_A preencher: idempotência de transferências e histórico de transações por conta._

## Checklist final de entrega

_A conferir ao final do sprint, item a item, contra a seção 13 do roteiro._
