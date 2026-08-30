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

**1. Por que a transferência local não precisa da lógica de `aoEnviar()`/`aoReceber()` do relógio de Lamport, enquanto a transferência entre agências precisa?**

`aoEnviar()` e `aoReceber()` existem para carimbar e ajustar o relógio lógico exatamente no momento em que uma **mensagem atravessa a rede entre dois processos diferentes** — é aí que a causalidade entre eventos de processos distintos precisa ser preservada (regras 2 e 3 de Lamport). Numa transferência local, o débito e o crédito acontecem **dentro da mesma agência, no mesmo processo, na mesma thread da requisição** — não existe nenhuma mensagem sendo enviada para outro processo, então não há "outro relógio" com quem sincronizar. Por isso a transferência local usa `eventoLocal()` duas vezes (uma para o débito, outra para o crédito, cada uma como seu próprio evento — comprovado no log real: `TRANSFERENCIA_DEBITO` timestamp 3 seguido de `TRANSFERENCIA_CREDITO` timestamp 4 na mesma agência), enquanto a transferência entre agências usa `eventoLocal()` para o débito e depois `aoEnviar()` para carimbar o valor que vai viajar na requisição HTTP até a outra agência, que por sua vez usa `aoReceber()` ao processar `creditar-remoto`.

**2. Reproduza a falha conhecida e observe o saldo da conta de origem depois do erro. Ele foi revertido? O que isso significa em termos de consistência do sistema bancário?**

Testado de verdade: com a Agência 0 e a Agência 1 no ar, criei a conta 0 (Agência 0, saldo 100) e a conta 1 (Agência 1, saldo 50) e fiz duas transferências de 0 para 1. Depois de uma transferência bem-sucedida de 20 (saldo da conta 0 caiu para 50), **derrubei o processo da Agência 1** e tentei uma nova transferência de 15. A resposta foi:

```
HTTP 502
{"erro":"Falha ao contatar agencia de destino. Debito ja aplicado - inconsistencia conhecida (ver Sprint 4)."}
```

E o saldo da conta de origem, consultado logo em seguida, ficou em **35** (100 − 20 − 15) — ou seja, **o débito não foi revertido**. O log confirmou exatamente isso: um evento `TRANSFERENCIA_DEBITO` (timestamp 7) seguido de `TRANSFERENCIA_FALHOU` (timestamp 9) com o erro real (`Connection refused`), sem nenhum evento de estorno.

Em termos de consistência bancária, isso é uma violação da propriedade de **atomicidade** que qualquer transação financeira precisa ter: ou a operação inteira acontece (débito e crédito), ou nenhuma parte dela acontece — nunca só metade. Aqui, os R$15 saíram da conta de origem e não entraram em lugar nenhum: momentaneamente "sumiram" do sistema como um todo (embora continuem corretamente subtraídos do lado que os debitou). Isso é exatamente o tipo de inconsistência que uma transação distribuída de verdade (2PC ou Saga, Sprint 4) existe para evitar.

**3. Duas formas possíveis de corrigir esse problema no Sprint 4 (alto nível, sem implementar agora):**

- **Two-Phase Commit (2PC):** antes de aplicar o débito de verdade, a agência de origem pergunta à agência de destino "você consegue receber esse crédito?" (fase de *prepare*) e só efetiva o débito e pede para a outra agência efetivar o crédito depois que ambas confirmarem que estão prontas (fase de *commit*). Se a agência de destino não responder ou recusar na fase de preparação, a origem simplesmente nunca aplica o débito — nada precisa ser revertido, porque nada foi aplicado de forma definitiva ainda.
- **Saga (compensação):** o débito é aplicado imediatamente (como hoje), mas cada etapa da transação registra uma **ação de compensação** correspondente. Se uma etapa posterior falhar (como a chamada `creditar-remoto`), o sistema executa automaticamente a compensação da etapa anterior — nesse caso, um crédito de estorno na conta de origem, disparado pelo próprio sistema ao detectar a falha, em vez de simplesmente logar a inconsistência e deixar o saldo desbalanceado como acontece hoje.

## Parte E — Linha do tempo unificada

Ao rodar `MesclarLogs` depois de criar uma conta em cada uma das 3 agências (sem que elas nunca tivessem se comunicado antes) e fazer uma transferência entre agências, a saída real foi:

```
=== Linha do tempo unificada (ordenada por relogio de Lamport) ===
[Lamport 1] (2026-08-30T20:51:14.214938700Z) agencia-0 - CRIACAO_CONTA {titular=Ana, saldoInicial=100}
[Lamport 1] (2026-08-30T20:51:14.448899900Z) agencia-2 - CRIACAO_CONTA {titular=Duda, saldoInicial=100}
[Lamport 1] (2026-08-30T20:51:14.687526500Z) agencia-1 - CRIACAO_CONTA {saldoInicial=50, titular=Carla}
[Lamport 2] (2026-08-30T20:51:14.782119500Z) agencia-0 - TRANSFERENCIA_DEBITO {valor=20, idDestino=1}
[Lamport 4] (2026-08-30T20:51:14.856469400Z) agencia-1 - TRANSFERENCIA_CREDITO_REMOTO {valor=20, origemAgencia=0}
```

**Empate real, encontrado sem precisar forçar nada:** os três primeiros eventos (`CRIACAO_CONTA` em `agencia-0`, `agencia-2` e `agencia-1`) têm **o mesmo `timestampLamport` (1)** — porque era o primeiro evento local de cada uma das três agências, e elas nunca tinham trocado nenhuma mensagem entre si até aquele momento. Também dá pra ver a regra 2/3 funcionando entre `agencia-0` e `agencia-1`: o débito ficou com timestamp 2, o `aoEnviar()` internamente avançou para 3 (não vira um evento próprio, só carimba a mensagem), e a agência 1 aplicou `max(1, 3) + 1 = 4` ao receber — exatamente o valor 4 que aparece no log.

**1. O relógio de Lamport garante `timestamp(A) < timestamp(B)` se A aconteceu antes de B causalmente, mas não garante a volta. O que isso significa na prática ao ver dois eventos com timestamps diferentes, sem saber se um influenciou o outro?**

Significa que a ordem dos timestamps na linha do tempo é **confiável em uma direção só**: se eu vejo `timestamp(A) < timestamp(B)`, isso **não me diz** se A realmente causou B ou se A e B são só dois eventos concorrentes que, por acaso (ou por causa do incremento monotônico de cada relógio), acabaram numerados nessa ordem. Por exemplo, os três `CRIACAO_CONTA` do exemplo acima aparecem na ordem agencia-0, agencia-2, agencia-1 simplesmente porque foi a ordem em que os `curl` chegaram a cada servidor (concorrência de fato, sem nenhuma relação causal entre eles) — mas se os timestamps não tivessem empatado (por exemplo, se a agencia-2 já tivesse processado outro evento antes), a leitura ingênua da linha do tempo poderia sugerir uma relação de causa e efeito que simplesmente não existe. Ou seja: o relógio de Lamport prova ausência de causalidade quando os timestamps estão "fora de ordem" de um jeito impossível, mas **nunca prova presença de causalidade** só porque um timestamp é menor que outro.

**2. O relógio de Lamport, sozinho, seria suficiente para distinguir com certeza "A e B são concorrentes" de "A aconteceu antes de B"? Por que isso motiva o relógio vetorial do Sprint 2?**

Não. O exemplo capturado acima é a prova prática disso: os três eventos de criação de conta têm timestamps **diferentes** entre si na saída ordenada (1, mas com desempate por hora de parede/agência para ordená-los na exibição) mesmo sendo **genuinamente concorrentes** (nenhum influenciou o outro) — e, olhando só para os números, não existe nenhuma forma de provar isso a partir do relógio escalar de Lamport; a gente só sabe que são concorrentes porque conhece o cenário de teste (sabemos que essas agências nunca trocaram mensagens antes daquele ponto). Em um sistema real, sem esse conhecimento de bastidores, dois eventos com timestamps de Lamport diferentes são **ambíguos**: pode ser causalidade, pode ser concorrência disfarçada de ordem. É exatamente essa ambiguidade que motiva o relógio vetorial (Sprint 2): em vez de um único contador escalar por processo, cada processo mantém um vetor com o "conhecimento" que tem do progresso de *todos* os processos do sistema — o que permite comparar dois timestamps vetoriais e concluir, com certeza matemática, se um domina o outro (causalidade) ou se nenhum domina o outro (concorrência real), sem precisar de conhecimento externo sobre o cenário.

## Parte F — Autenticação (JWT)

_A preencher após a implementação da Parte F, incluindo a justificativa do modelo de login escolhido e da autenticação entre agências (creditar-remoto)._

## Parte G — Frontend

_A preencher após a implementação da Parte G._

## Funcionalidades adicionais

_A preencher: idempotência de transferências e histórico de transações por conta._

## Checklist final de entrega

_A conferir ao final do sprint, item a item, contra a seção 13 do roteiro._
