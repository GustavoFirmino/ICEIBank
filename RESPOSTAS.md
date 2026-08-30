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

### Justificativa: formato das credenciais de login

Optei por um **usuário de aplicativo separado da conta bancária** (login/senha em memória, cadastrado no `application.yml`, sem nenhum vínculo 1:1 com um número de conta específico) em vez de usar o id da conta + senha como credencial. O JWT emitido, portanto, prova apenas "alguém autenticado está fazendo esta requisição" — ele **não** carrega nem restringe quais contas essa pessoa pode operar. Essa é uma simplificação deliberada do Sprint 1: implementei autenticação, mas não autorização granular por conta (ver pergunta 1 abaixo, que discute exatamente essa lacuna). Achei mais honesto deixar essa limitação explícita e documentada do que fingir uma autorização por conta que não existe de verdade só para "parecer" mais completo.

### Justificativa: autenticação da chamada interna entre agências (`creditar-remoto`)

A rota `POST /contas/{id}/creditar-remoto` **não** exige o JWT do usuário — em vez disso, exige um header `X-Internal-Key` com um segredo compartilhado entre as 3 agências (`agencia.internal-key`, no `application.yml` compartilhado). A alternativa seria repassar o token JWT de quem pediu a transferência para a chamada entre agências, mas isso misturaria dois conceitos diferentes: "quem é o usuário" (autenticação de pessoa) e "quem está chamando este serviço" (autenticação de serviço/máquina). Repassar o token do usuário faria a agência de origem literalmente **se passar pelo usuário** perante a agência de destino — um padrão arquiteturalmente estranho e frágil (por exemplo, se o token do usuário expirasse no meio do caminho, a chamada interna falharia por um motivo que não tem nada a ver com a saúde da comunicação entre agências). Uma chave de serviço própria, sem prazo de expiração amarrado à sessão de nenhum usuário, mantém as duas responsabilidades de autenticação claramente separadas.

### Perguntas

**1. Qual a diferença entre autenticação e autorização? Sua implementação verifica só uma das duas, ou as duas? Um usuário autenticado consegue sacar de uma conta que não é dele?**

**Autenticação** responde "quem é você?" — confirma a identidade de quem está fazendo a requisição. **Autorização** responde "o que você pode fazer?" — decide se essa identidade tem permissão para a ação específica que está tentando executar. Minha implementação verifica **apenas autenticação**: o `JwtAuthFilter` confirma que existe um JWT válido e assinado corretamente, mas nunca checa se o `username` daquele token tem qualquer relação com o `id` da conta sendo movimentada. Testei isso na prática: com o usuário `gustavo` autenticado, consegui sacar/depositar/transferir em **qualquer** conta existente na agência, não só em contas "dele" — porque, na verdade, neste sprint não existe nem o conceito de "conta de um usuário" (ver justificativa acima). Ou seja: autenticação sim, autorização por conta não — uma limitação real e consciente do Sprint 1.

**2. Por que o servidor não precisa consultar um banco de dados para validar a assinatura de um JWT a cada requisição? O que isso implica sobre escalabilidade, comparado a guardar sessões em memória no servidor?**

Porque a validade do token pode ser conferida **matematicamente**, só com a chave secreta que o servidor já tem localmente: o `JwtService.validarToken()` recalcula a assinatura HMAC dos dados do token com a chave (`Keys.hmacShaKeyFor(...)`) e compara com a assinatura que veio junto — se baterem, o token não foi adulterado e realmente foi emitido por quem tem a chave. Não é preciso perguntar a lugar nenhum "esse token ainda é válido?", porque toda a informação necessária (usuário, data de emissão, data de expiração) já está dentro do próprio token, verificável offline. Isso é bem diferente de sessões guardadas em memória no servidor (`HttpSession`), onde o servidor precisa manter um registro de cada sessão ativa — o que implica: (a) qualquer instância da aplicação que receba a requisição precisa ter acesso a esse registro (exigindo sessões compartilhadas/replicadas entre múltiplas instâncias, ou "sticky sessions" amarrando um cliente sempre ao mesmo servidor), e (b) o estado de quem está logado vive no servidor, não no cliente. Com JWT, qualquer uma das 3 agências poderia validar um token emitido por qualquer uma das outras (já que a chave é compartilhada), sem nenhuma coordenação entre elas — o que é exatamente o tipo de propriedade que facilita escalar horizontalmente (adicionar mais instâncias sem se preocupar em sincronizar sessão nenhuma).

**3. O que aconteceria com a segurança do sistema se a chave secreta usada para assinar o JWT vazasse?**

Seria uma falha de segurança grave e total: qualquer pessoa de posse da chave conseguiria **forjar tokens válidos para qualquer usuário** (bastaria montar um JWT com o `sub` que quisesse e assiná-lo com a chave vazada) — o sistema aceitaria esses tokens forjados como legítimos, já que a validação é puramente matemática e não consulta nenhuma outra fonte de verdade. Isso permitiria a um atacante se autenticar como qualquer usuário (inclusive um que nem exista de verdade) e operar livremente sobre qualquer conta, sem nenhuma senha. A correção, nesse cenário, exigiria trocar a chave secreta nas 3 agências simultaneamente (invalidando de uma vez todos os tokens já emitidos, inclusive os legítimos) — o que reforça por que essa chave nunca deveria estar hardcoded em texto puro num repositório público de verdade (no nosso caso, ela está no `application.yml` só porque é um projeto acadêmico de demonstração; em produção, isso pertenceria a um cofre de segredos/variável de ambiente, fora do controle de versão).

## Parte G — Frontend

_A preencher após a implementação da Parte G._

## Funcionalidades adicionais

O roteiro pede pelo menos uma funcionalidade adicional (seção 2.1). Implementei duas:

### 1. Idempotência de transferências

**O que faz:** `POST /transferencias` aceita um campo opcional `idOperacao` (uma string única por operação, ex.: um UUID gerado pelo cliente). Se a mesma requisição for reenviada com o mesmo `idOperacao` — por exemplo, porque o cliente não recebeu a resposta da primeira tentativa por um timeout de rede e não sabe se ela foi aplicada — a transferência **não é processada de novo**: a API devolve a mesma resposta da primeira vez, com um campo extra `repetida: true`, e o saldo das contas não muda uma segunda vez.

**Por que escolhi essa:** é o tipo de problema que só aparece de verdade em um sistema distribuído — em uma chamada local, "chamar duas vezes por engano" quase nunca é uma preocupação séria, mas numa rede real (a mesma rede que já vimos derrubar transferências entre agências na Parte D), retries por timeout são o normal, não a exceção. Sem idempotência, um cliente que reenvia por segurança (achando que a primeira tentativa falhou) corre o risco de debitar a mesma conta duas vezes. Implementar isso aqui conecta diretamente com o tema central do sprint: mais uma consequência prática de operar em um ambiente onde mensagens podem se perder ou demorar.

**Como testei:** `TransferenciaServiceTest.transferenciaComMesmoIdOperacaoNaoEAplicadaDuasVezes` e teste manual via `curl` (evidência em `evidencias/sprint1/funcionalidade-adicional.png`): enviei a mesma transferência duas vezes com o mesmo `idOperacao` e confirmei que o saldo só mudou uma vez, com a segunda resposta marcada `repetida: true`.

**Implementação:** `IdempotencyStore` (mapa em memória `idOperacao -> resposta`, só na agência de origem) + `TransferenciaService.transferir` verificando o cache antes de executar. Quando `idOperacao` não é informado, a transferência funciona exatamente como no escopo obrigatório do roteiro (sem nenhuma mudança de comportamento) — a funcionalidade é aditiva, não substitui nada.

### 2. Histórico de transações por conta

**O que faz:** `GET /contas/{id}/historico` lista todos os eventos já registrados para aquela conta nesta agência (criação, depósitos, saques, transferências enviadas/recebidas/revertidas), na ordem em que aconteceram, cada um com seu timestamp de Lamport e a hora de parede.

**Por que escolhi essa:** o sistema já registra cada operação como um evento (para a linha do tempo da Parte E) — expor isso por conta é reaproveitar uma estrutura que já existia, sem duplicar lógica, e é o tipo de funcionalidade que qualquer usuário de um banco de verdade esperaria (um extrato).

**Como testei:** `ContaServiceTest.historicoListaOsEventosNaOrdemEmQueAconteceram`, `historicoDeContaInexistenteLancaExcecao`, `historicoNaoMisturaEventosDeOutraConta`, e teste manual via `curl` confirmando que `GET /contas/0/historico` devolve a lista correta de eventos (evidência em `evidencias/sprint1/funcionalidade-adicional.png`).

**Implementação:** `EventLogService.historicoDaConta(id)` (já existia, criado junto com o registro de eventos da Parte B) filtra a lista de eventos em memória por `idConta`; `ContaService.historico(id)` valida que a conta existe nesta agência (404 caso não) e mapeia para `HistoricoEventoResponse`; exposto via `GET /contas/{id}/historico`.

## Checklist final de entrega

_A conferir ao final do sprint, item a item, contra a seção 13 do roteiro._
