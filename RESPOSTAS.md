# Respostas — ICEIBank

> **Índice:** [Sprint 1](#respostas--iceibank-sprint-1) (este documento, primeira parte) · [Sprint 2 — mensageria e relógio vetorial](#respostas--iceibank-sprint-2) (a partir da metade do arquivo) · [Fluxo de Execução (Sprint 2)](FLUXO-DE-EXECUCAO.md)

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

**Evidência final (`evidencias/sprint1/linha-do-tempo.png`, execução de 07/09/2026):** ao gerar a linha do tempo completa da entrega (4 contas, depósito, transferência local, entre agências, idempotência e a falha conhecida), apareceu um segundo empate, mais interessante que o das criações de conta: `[Lamport 8] agencia-1 - TRANSFERENCIA_CREDITO_REMOTO` e `[Lamport 8] agencia-0 - TRANSFERENCIA_DEBITO {idDestino=3}`. Pela hora de parede, o crédito remoto na agência 1 aconteceu **antes** (…:16.111) do débito seguinte na agência 0 (…:16.172) — e, na prática, foi mesmo: a agência 0 só iniciou a transferência seguinte depois de receber a resposta HTTP da agência 1. Mas o relógio de Lamport não sabe disso, porque a implementação só aplica a regra 3 (`aoReceber`) na *requisição* `creditar-remoto`, não na *resposta* que volta para a origem. Ou seja: existe uma relação causal real (resposta → próxima operação) que o sistema não modela como "mensagem recebida", e por isso os dois eventos ficam com o mesmo timestamp, como se fossem concorrentes. É um exemplo concreto de que o relógio lógico só enxerga a causalidade que o próprio código decide carimbar.

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

Implementado em React + Vite (`frontend/`). Fluxo completo testado de ponta a ponta pela interface: login, consulta de saldo, depósito, saque, transferência local, transferência entre agências (confirmada via API: saldo debitado em uma agência e creditado na outra, através do próprio frontend) e um caso de erro (saque com saldo insuficiente, exibido em um banner vermelho na tela).

**1. Como o frontend "lembra" de reenviar o token depois do login?**

O token vem na resposta de `POST /auth/login` e é guardado de duas formas ao mesmo tempo: no estado do `AuthContext` (React) e no `localStorage` do navegador (`iceibank.token`). Toda chamada à API passa por um único ponto central — `api/httpClient.js` (`apiFetch`) — que recebe o token como parâmetro e, se ele existir, anexa o header `Authorization: Bearer <token>` automaticamente antes de disparar o `fetch`. Como cada componente (saldo, depósito, saque, transferência, histórico) usa o hook `useAuth()` para pegar o token atual e repassá-lo para sua chamada de API, ninguém precisa "lembrar" manualmente — é estrutural, não uma decisão tomada em cada tela. O `localStorage` também garante que, se a página for recarregada, a pessoa continua logada (o `AuthContext` lê o token salvo na inicialização).

**2. Se o token expirar no meio de uma operação, o que acontece? A interface avisa, ou só mostra um erro genérico?**

A interface avisa explicitamente. Dois mecanismos cobrem isso:
- **Reativo:** se uma chamada à API retornar 401 (token ausente/expirado/inválido), `useApiError` intercepta esse status especificamente, desloga automaticamente (`logout()`) e devolve a mensagem "Sua sessão expirou ou o token é inválido. Faça login novamente." — exibida no `ErrorBanner` da tela onde a ação foi tentada, não um erro genérico de console.
- **Proativo:** ao abrir o app (ou recarregar a página), o `AuthContext` decodifica o campo `exp` do JWT salvo (`utils/jwt.js`, sem validar assinatura — isso é sempre trabalho do backend) e, se ele já estiver expirado, desloga imediatamente, evitando mandar uma requisição que a API certamente rejeitaria.

Testado na prática durante o desenvolvimento: o token (5 minutos de validade) expirou entre duas sessões de teste e o app deslogou sozinho ao ser reaberto, exigindo login de novo — confirmando que o mecanismo reativo funciona de verdade, não só em teoria.

**3. Onde ficam o Model, a View e o Controller no frontend? Estão separados, ou o código ficou mais misturado do que o padrão sugere?**

Ficaram razoavelmente separados, com uma ressalva:
- **Model** — pasta `api/` (`httpClient.js`, `authApi.js`, `contasApi.js`, `transferenciasApi.js`, `designSystemApi.js`): é a única camada que sabe conversar com a API REST (monta URLs, serializa JSON, normaliza erros em `AppError`). Nenhum componente de tela faz `fetch` diretamente.
- **View** — pasta `components/` (`LoginForm`, `ContaBalance`, `DepositoSaqueForm`, `TransferenciaForm`, `HistoricoList`, `ErrorBanner`, `BranchSelector`) e `pages/` (`LoginPage`, `DashboardPage`): só renderizam UI e capturam eventos do usuário; não sabem nada sobre `fetch`, token ou como a API está estruturada.
- **Controller** — `context/` (`AuthContext`, `BranchContext`) + `hooks/` (`useAuth`, `useBranch`, `useApiError`): orquestram estado (token, agência selecionada) e a lógica de "o que fazer quando a chamada falha", conectando a View ao Model sem que a View precise conhecer detalhes de nenhum dos dois.

A ressalva: em componentes como `TransferenciaForm.jsx` ou `DepositoSaqueForm.jsx`, a View **também** contém uma pequena fatia de lógica de orquestração (chamar a função da API dentro de um `try/catch`, decidir qual `useState` atualizar em cada caso) — no React, com hooks, é comum essa borda entre View e Controller ficar menos rígida do que num MVC clássico (onde um Controller separado receberia o evento primeiro). Não achei isso um problema grave para o tamanho deste projeto, mas é honesto reconhecer que a separação não é 100% limpa — um projeto maior provavelmente extrairia esses formulários para hooks próprios (ex.: `useTransferencia()`) para isolar essa lógica de vez.

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

Conferido item a item contra a seção 13 do roteiro, a partir de um **clone limpo** do repositório (`git clone` em pasta nova → `mvnw package` com os 65 testes → `npm ci && npm run build`), em 07/09/2026:

- [x] Repositório Git com a estrutura de pastas indicada e histórico incremental (30+ commits ao longo do sprint, um por parte concluída — não um commit único no fim).
- [x] As 3 agências rodando simultaneamente com o mesmo `.jar` e perfis diferentes (`agencia0/1/2` → portas 4047/4048/4049), cada uma respondendo só pela sua partição (`id % 3`) — evidência: `transferencia-entre-agencias.png` (conta 0 só responde em 4047, conta 1 só em 4048).
- [x] CRUD de contas + depósito/saque, cada operação registrada com timestamp de Lamport — evidência: `auth-com-token.png` (depósito) e o log `.jsonl` visível em `transferencia-local.png`.
- [x] Transferência local e entre agências funcionando — evidências: `transferencia-local.png`, `transferencia-entre-agencias.png`.
- [x] Falha conhecida reproduzida e documentada (Agência 2 derrubada → 502, débito **não** revertido, `TRANSFERENCIA_FALHOU` no log) — evidência: `falha-conhecida.png`; discussão na Parte D.
- [x] Script de linha do tempo (`MesclarLogs`) funcionando e usado para observar concorrência real (dois empates de Lamport analisados na Parte E) — evidência: `linha-do-tempo.png`.
- [x] Autenticação JWT protegendo as rotas, com os três cenários — evidências: `auth-sem-token.png` (401), `auth-com-token.png` (200), `auth-token-expirado.png` (401 com `JWT expired`).
- [x] Frontend funcional consumindo a API autenticada: login, saldo, depósito, saque, transferência local e entre agências, erros visíveis — evidências: `frontend-login.png`, `frontend-transferencia.png`, `frontend-erro.png`.
- [x] Funcionalidades adicionais (duas): idempotência de transferências e histórico por conta — evidência: `funcionalidade-adicional.png`; commits próprios `feat(extra): ...`.
- [x] Pasta `evidencias/sprint1/` com os 11 prints indicados nas seções 4.2, 2.1, 11.2 e 12.2 — todos gerados a partir de execuções reais, com `Get-Date` visível.
- [x] `RESPOSTAS.md` com as questões das seções 6.4 (Parte B), 8.3 (Parte D), 10.3 (Parte E), 11.3 (Parte F) e 12.3 (Parte G), a descrição das funcionalidades adicionais e as justificativas de design das Partes F e G.
- [x] Vídeo de apresentação (critério 14, 2 pontos) — gravado em 07/09/2026 e publicado como asset da release [`v1.0-sprint1`](https://github.com/GustavoFirmino/ICEIBank/releases/tag/v1.0-sprint1): [iceibank-sprint1-apresentacao.mp4](https://github.com/GustavoFirmino/ICEIBank/releases/download/v1.0-sprint1/iceibank-sprint1-apresentacao.mp4).

**Declaração de uso de IA (nota de transparência do roteiro):** usei o Claude (Anthropic) como apoio para rascunhar, revisar e testar código e texto ao longo do sprint. Todo o código entregue foi executado e verificado por mim nesta máquina; consigo explicar e defender qualquer trecho.


---
---

# Respostas — ICEIBank, Sprint 2

**Tema:** comunicação indireta (mensageria / Publish-Subscribe com RabbitMQ) e relógio vetorial. Este sprint evolui o código do Sprint 1 no mesmo repositório (o estado do Sprint 1 está preservado na release [`v1.0-sprint1`](https://github.com/GustavoFirmino/ICEIBank/releases/tag/v1.0-sprint1)).

**Ambiente usado nas execuções:** RabbitMQ 4.3.6 (Erlang 27) em container Docker local (`.\iniciar-rabbitmq.ps1`) — o roteiro permite `RABBITMQ_URL=amqp://localhost` no lugar do CloudAMQP (a aplicação lê a URL da variável de ambiente e funciona igual nos dois casos). 3 agências reais (portas 4047–4049), Spring AMQP, **110 testes automatizados** passando. Todas as evidências de [`evidencias/sprint2/`](evidencias/sprint2) são a saída real dos comandos; a atividade de recapitulação do sprint está respondida em [`FLUXO-DE-EXECUCAO.md`](FLUXO-DE-EXECUCAO.md).

## Parte A — RabbitMQ

Topologia criada pelas próprias agências ao subir (conferida no painel do RabbitMQ Manager — [`rabbitmq-manager-filas.png`](evidencias/sprint2/rabbitmq-manager-filas.png), [`rabbitmq-manager-exchange.png`](evidencias/sprint2/rabbitmq-manager-exchange.png)):

| Item | Valor |
|---|---|
| exchange | `iceibank.eventos`, tipo **topic**, **durável** |
| filas | `fila-agencia-0/1/2`, **duráveis**, ligadas por `agencia.<id>.creditar` |
| dead-letter (funcionalidade adicional) | exchange `iceibank.eventos.dlx` → fila `fila-creditos-mortos` |
| mensagens | JSON, `delivery_mode = persistent` (gravadas em disco), com *publisher confirm* |

**Onde diverge do exemplo em Node do roteiro (e por quê):** (1) Spring AMQP no lugar de `amqplib` (o roteiro manda usar a mesma linguagem do Sprint 1 — Java); (2) **todas** as agências declaram **as três filas**, não só a sua — declarar é idempotente, e assim uma mensagem para uma agência que ainda nunca subiu fica retida em vez de ser descartada por "nenhuma fila ligada"; (3) a publicação só vale depois do *publisher confirm* (no exemplo, `publish()` volta na hora e "publicada" não provaria nada); (4) cada mensagem carrega um `idMensagem` único.

## Parte B — Relógio vetorial

O `RelogioVetorialService` substitui o `LamportClockService`: vetor com **uma posição por agência**, três regras, todos os métodos `synchronized` (o vetor é compartilhado entre as threads HTTP do Tomcat e as do consumidor RabbitMQ) e devolvendo **cópias imutáveis** (o vetor gravado em um evento nunca muda depois). O log de eventos, o histórico por conta e o frontend passaram a carregar `timestampVetorial`. Testes: as 3 regras, os exemplos abaixo e 8 threads × 1000 operações sem perder nenhum incremento.

**1. Com 3 agências o vetor tem 3 posições. Com 10, o que acontece com o tamanho do vetor anexado a cada mensagem? É um problema?**

O vetor cresce **linearmente com o número de processos**: com 10 agências, cada mensagem carrega 10 contadores (e cada comparação percorre 10 posições); com 1000, seriam 1000 contadores em toda mensagem e em todo evento gravado. Para 3 ou 10 agências **não é problema** — são alguns bytes no meio de uma mensagem JSON de ~150 bytes. Passa a ser um problema quando o número de processos é grande ou **dinâmico**: (a) o overhead por mensagem/evento escala com *N*; (b) o vetor precisa saber de antemão **quem são** os participantes (uma agência nova exige redimensionar todos os vetores existentes). Aqui a posição é por **agência**, não por conta — então o vetor cresce com o nº de agências, não de contas, o que ajuda. Em escala, usam-se variações (relógios de versão/*dotted version vectors*, *interval tree clocks*, ou relógios híbridos com custo constante) trocando um pouco de precisão por tamanho.

**2. `V1 = [3,1,0]` e `V2 = [3,2,0]`: qual aconteceu primeiro, ou são concorrentes?**

Comparando posição a posição: `3 ≤ 3`, `1 ≤ 2`, `0 ≤ 0` — **`V1 ≤ V2` em toda posição** e os vetores são diferentes. Logo, **o evento de `V1` aconteceu antes** do de `V2` (e pode tê-lo influenciado): tudo o que `V1` "conhece" `V2` também conhece, e `V2` conhece mais um evento da agência 1. (Coberto por `VetoresTest.pergunta642…`.)

**3. `V1 = [3,1,0]` e `V2 = [1,3,0]`: qual aconteceu primeiro, ou são concorrentes?**

Posição 0: `3 > 1` → `V1` **não** é `≤ V2`. Posição 1: `1 < 3` → `V2` **não** é `≤ V1`. Como **nenhum domina o outro**, os eventos são **concorrentes**: nenhum influenciou o outro (cada um conhece eventos que o outro desconhece — `V1` viu 3 eventos da agência 0 e `V2` só 1; `V2` viu 3 da agência 1 e `V1` só 1). (`VetoresTest.pergunta643…`.) Um caso real da execução: a criação da conta 0 na Agência 0 (`[1,0,0]`) e a da conta 1 na Agência 1 (`[0,1,0]`) — concorrentes.

## Parte C — Publish/Subscribe entre agências

**Observações da tarefa 7.4 (execução real).**

1. *Transferência normal, ambas no ar* — 0 → 1, valor 30: saldos 1000 → 970 e 500 → 530. Log da origem: `TRANSFERENCIA_DEBITO [2,0,0]` e `TRANSFERENCIA_PUBLICADA [3,0,0]`; log do destino: `TRANSFERENCIA_CREDITO_REMOTO [3,2,0]` — a Agência 1 estava em `[0,1,0]`, recebeu `[3,0,0]`, `max` = `[3,1,0]`, +1 na própria posição = `[3,2,0]` (regra 3). → [`transferencia-assincrona.png`](evidencias/sprint2/transferencia-assincrona.png)
2. *Teste de resiliência* — com a Agência 1 derrubada, a transferência para uma conta dela (valor 200) **respondeu 200**; a mensagem ficou **retida** na `fila-agencia-1` (1 pronta, 0 consumidores).
3. *A Agência 1 volta* — reiniciou **sem a conta 1** (as contas são em memória). O log mostra `CREDITO_REMOTO_FALHOU [5,1,0]` (motivo: conta não encontrada nesta agência). A mensagem **foi entregue**, mas a conta **não existia** para receber o crédito. → [`resiliencia-fila.png`](evidencias/sprint2/resiliencia-fila.png)
4. *JWT e frontend continuam funcionando* — [`regressao-jwt.png`](evidencias/sprint2/regressao-jwt.png), [`regressao-frontend-transferencia.png`](evidencias/sprint2/regressao-frontend-transferencia.png) e [`regressao-frontend-saldo-destino.png`](evidencias/sprint2/regressao-frontend-saldo-destino.png).

**1. No passo 4, o que aconteceu exatamente quando a Agência 1 voltou? Se a mensagem "sumiu" (não foi aplicada), foi porque a mensageria falhou ou por outro motivo?**

**A mensageria não falhou — ela fez exatamente o que promete.** Sequência: a Agência 1 reconecta → o RabbitMQ entrega a mensagem que estava retida na fila durável (`vetorEnvio [5,0,0]`) → o consumidor chama `aoReceber` (o vetor da agência vira `[5,1,0]`: receber já é um evento) → tenta creditar a conta 1 → **a conta não existe** (a agência reiniciou e perdeu as contas em memória) → `CREDITO_REMOTO_FALHOU`. O motivo é o **estado** (contas em memória), não o transporte: entre "a mensagem chegou" e "o crédito foi aplicado" há uma lacuna que a mensageria não cobre. Vale notar que, **no exemplo em Node do roteiro**, o consumidor só registra o erro e dá `ack` — a mensagem seria confirmada e descartada, e o dinheiro sumiria **em silêncio** (débito de 200 na origem, crédito em lugar nenhum). Na minha implementação a mensagem é **rejeitada sem reentrega** e o RabbitMQ a encaminha para a **dead-letter queue** (`x-death: rejected`, fila original `fila-agencia-1`), retida **com todos os dados**, de onde pode ser reprocessada — ver "Funcionalidade adicional".

**2. Compare com o Sprint 1 (REST direto): o que melhorou e o que continua sendo um problema em aberto?**

*Melhorou:* (a) a agência de destino fora do ar **deixou de ser um erro** — a mensagem espera na fila durável (Sprint 1: 502 na hora e débito pendurado, sem nada guardado); (b) **desacoplamento temporal** — origem e destino não precisam estar no ar ao mesmo tempo, e a thread HTTP da origem não fica bloqueada esperando o destino; (c) o *publisher confirm* dá certeza de que o broker aceitou; (d) se o **broker** cair, o débito é **estornado** e a transferência falha com 503 (o Sprint 1 deixava o débito pendurado) — verificado, [`broker-fora-do-ar.png`](evidencias/sprint2/broker-fora-do-ar.png); (e) o consumidor é idempotente por `idMensagem` (entrega duplicada não duplica crédito) — [`entrega-duplicada.png`](evidencias/sprint2/entrega-duplicada.png).

*Continua em aberto* — a diferença entre **"a mensagem não se perde"** e **"o sistema está correto"**: (1) enquanto o crédito não é aplicado (ex.: mensagem na dead-letter), o dinheiro está **debitado e não creditado** — a consistência é apenas *eventual*, e só se alguém reprocessar; (2) **débito local + publicação não são atômicos** — uma queda entre os dois perde a mensagem (o padrão *outbox* resolveria); (3) o estado é em memória: o conjunto de `idMensagem` já aplicados some no restart (com banco, o "já apliquei" teria que ser gravado na **mesma transação** do crédito — padrão *inbox*); (4) o significado do 200 mudou ("o broker aceitou", não "o crédito foi aplicado") e o cliente não tem confirmação do crédito (uma `TransferenciaConfirmada` fecharia o ciclo); (5) o dinheiro "em trânsito" some das somas de saldo por um instante. Garantir atomicidade entre agências é o Sprint 4 (2PC/Saga).

**3. O consumidor processa créditos sem passar por verificação de token JWT. Isso é um problema de segurança?**

**Sim, é uma superfície real — mas o JWT não é a ferramenta certa para fechá-la.** O JWT autentica **usuários da API HTTP**; o consumidor não recebe requisições HTTP, ele consome mensagens que o broker já aceitou. A fronteira de confiança ali é a **credencial do RabbitMQ**: quem consegue publicar na exchange `iceibank.eventos` com a routing key `agencia.1.creditar` consegue **criar dinheiro** em qualquer conta daquela agência. Eu **provei isso** na execução: em [`entrega-duplicada.png`](evidencias/sprint2/entrega-duplicada.png) publiquei uma mensagem **direto no RabbitMQ**, sem nenhum token, e ela creditou a conta. No meu ambiente de desenvolvimento, quem pode publicar é qualquer processo com a credencial `guest/guest` que consiga alcançar a porta 5672. **Verifiquei** que, na imagem oficial do RabbitMQ em Docker, o `guest` é aceito de **qualquer origem** (`loopback_users = []`) — e a primeira versão do meu script publicava as portas em `0.0.0.0`, o que numa rede de laboratório permitiria a qualquer máquina publicar mensagens. Corrigi: `iniciar-rabbitmq.ps1` agora publica as portas só em `127.0.0.1` (e recria um container antigo que estivesse aberto). Com o CloudAMQP, a URL AMQP dá acesso de qualquer lugar a quem a tiver. Mitigações reais: um **usuário por agência** com permissões mínimas (só publicar na exchange e ler a própria fila, com *topic permissions*), **TLS** (`amqps://`), a URL só em variável de ambiente (nunca no repositório — o `application.yml` só tem o padrão local), **vhosts** separados por ambiente e **assinar a mensagem** (HMAC com uma chave por agência de origem, verificada pelo consumidor) além de validar invariantes de negócio. Para o escopo deste sprint (ambiente local, sem dado real) é aceitável, mas não é algo a levar para produção como está.

## Parte D — Linha do tempo causal

O `MesclarLogs` (`.\linha-do-tempo.ps1`) agora imprime três seções: (1) a linha do tempo por hora de parede (**só exibição**); (2) os **pares concorrentes** entre agências diferentes (nenhum vetor domina o outro); (3) os **pares causais envio → recebimento**, ligados pelo `idMensagem`, com a relação **calculada** pelos vetores — que deve ser `ANTES`. → [`linha-do-tempo-causal.png`](evidencias/sprint2/linha-do-tempo-causal.png): 8 eventos, 10 pares concorrentes e o par causal `PUBLICADA [3,0,0] → CREDITO_REMOTO [3,2,0]` verificado como `ANTES` (e **ausente** da lista de concorrentes, como pede a tarefa).

**1. No Sprint 1, o Lamport não permitia essa análise. O que, no relógio vetorial, torna possível a comparação confiável?**

O vetor guarda **um contador por processo**, então um timestamp carrega **"quantos eventos de cada agência eu conheço"** — a *história causal* resumida. `V1 ≤ V2` em todas as posições significa exatamente "tudo o que o evento 1 conhecia, o evento 2 também conhece" — e isso vale **se e somente se** o evento 1 aconteceu antes do 2 (`e → f ⇔ V(e) < V(f)`). No Lamport, um único número **mistura** os processos e perde *de quem* eram os eventos vistos: garante só `e → f ⇒ L(e) < L(f)` (uma direção), então dois timestamps diferentes podem ser causais **ou** concorrentes. Um exemplo do próprio Sprint 1: as três criações de conta tinham todas o timestamp Lamport **1** — impossível provar que eram concorrentes; agora são `[1,0,0]`, `[0,1,0]` e `[0,0,1]`, e nenhum domina o outro.

**2. Um par que o script classificou como concorrente. Faz sentido?**

`#2 agencia-1 CRIACAO_CONTA [0,1,0]  ||  #4 agencia-0 TRANSFERENCIA_DEBITO [2,0,0]`. Faz sentido: criar a conta do Bruno na Agência 1 e debitar a conta da Ana na Agência 0 **não têm relação de causa e efeito** — nenhuma mensagem foi trocada entre as duas agências até ali (o vetor `[0,1,0]` tem a posição 0 zerada: a Agência 1 não conhece **nenhum** evento da Agência 0; e o `[2,0,0]` tem a posição 1 zerada: a Agência 0 não conhece nenhum da 1). Curiosamente, pela **hora de parede** a criação vem *antes* do débito — mas isso **não** implica causalidade: são independentes, e é exatamente isso que o vetor prova e o relógio físico não pode provar (relógios de máquinas diferentes não são sincronizados). Já o par débito `[2,0,0]` → crédito `[3,2,0]` **não** aparece na lista: `[2,0,0] ≤ [3,2,0]` em todas as posições, então o débito foi **antes** do crédito (causal).

**3. O algoritmo é O(n²) no número de eventos. É um problema com milhões de eventos? O que fazer para escalar?**

**É um problema:** com 10⁶ eventos são ~5·10¹¹ comparações de vetores — inviável. Formas de melhorar: (a) **só comparar entre agências diferentes** (já faço — na mesma agência os eventos são totalmente ordenados pelo contador próprio); (b) usar a propriedade de Fidge/Mattern: para dois eventos em processos diferentes, `e → f` se decide comparando **uma única posição** (`V(e)[i] ≤ V(f)[i]`, com *i* = processo de *e*), em O(1) em vez de O(N); (c) como os eventos de cada agência são **monotônicos**, dá para achar, por *busca binária*, o primeiro evento de cada outra agência que "conhece" um dado evento — reduzindo a análise a ~O(n log n); (d) análise **sob demanda** ("quem é concorrente de X?" = O(n)) em vez de todos os pares; (e) **janelas/checkpoints** (cortes consistentes) para não comparar eventos muito distantes no tempo; (f) particionar por conta/dia e processar em paralelo (map-reduce) ou em *stream*. Na prática, sistemas grandes não fazem análise de pares completa: usam o vetor só onde há risco de conflito.

## Funcionalidade adicional — dead-letter queue com inspeção e reprocessamento (seção 2.1)

**O que faz.** Um crédito que a agência de destino **não consegue aplicar** (o caso da Parte C: a agência reiniciou e perdeu as contas em memória) não é confirmado nem reentregue em loop: é **rejeitado sem reentrega** e o RabbitMQ o encaminha para a dead-letter queue `fila-creditos-mortos`, **retido com todos os dados**. Duas rotas novas (protegidas por JWT):

- `GET /mensagens-mortas` — lista os créditos retidos cuja conta de destino é **desta** agência;
- `POST /mensagens-mortas/reprocessar` — republica esses créditos na exchange. Só saem da dead-letter **depois do *publisher confirm*** (se a republicação falhar, continuam lá); se a conta ainda não existir, o consumidor os rejeita de novo e eles voltam para a fila (**reprocessar é seguro de repetir**); o `idMensagem` original é mantido (o consumidor continua deduplicando) e cada reprocessamento vira um evento local `CREDITO_REMOTO_REPROCESSADO` no log.

**Por que escolhi.** Das opções do roteiro, é a que fecha exatamente o problema que o próprio sprint pede para observar (Parte C, pergunta 1): sem ela, o dinheiro de uma mensagem que "chegou mas não achou a conta" some em silêncio; com ela, fica retido e **recuperável**.

**Evidência** — [`funcionalidade-adicional.png`](evidencias/sprint2/funcionalidade-adicional.png): `GET` lista a mensagem de 200; o 1º reprocessamento (sem a conta) a republica e ela **volta** para a fila; a conta 1 é recriada (500); o 2º reprocessamento a aplica — saldo **500 → 700** e a dead-letter fica vazia. Em [`ordem-invertida.png`](evidencias/sprint2/ordem-invertida.png) ela é usada em um cenário de duas transferências independentes.

**O que a execução real revelou (e foi corrigido):** o 1º reprocessamento devolveu `reprocessadas: 2` para uma única mensagem — a republicada era rejeitada de novo e voltava à mesma fila **enquanto o laço ainda lia**, e o laço a pegava outra vez (em teoria, até o limite de 500). Corrigido para percorrer só o que estava na fila no início (commit `7fae66b`). Também: o evento de reprocessamento agora tem o vetor tomado **antes** de publicar (o consumidor, em outra thread, pode aplicar o crédito antes de a thread voltar de `publicar()`).

**Limitações:** lista/reprocessa no máximo 500 mensagens por chamada; só as da própria agência; o reprocessamento é **manual** (não há retentativa automática com *backoff*); e qualquer usuário autenticado pode chamá-lo (não há autorização por papel — mesma lacuna auth × autorização do Sprint 1).

**Além do mínimo (comportamentos novos, também com testes):** consumidor **idempotente** por `idMensagem`; **estorno do débito** quando o broker recusa a mensagem (503) e falha **retentável** (o mesmo `idOperacao` pode ser reenviado depois que o broker volta); rota inexistente devolve 404 (antes, com token, devolvia 403).

## Continuidade do Sprint 1 e decisões que mudaram

| Item | Estado |
|---|---|
| Particionamento (`id % 3`) | igual — cada agência recusa contas que não são dela |
| JWT | igual e verificado por regressão ([`regressao-jwt.png`](evidencias/sprint2/regressao-jwt.png)): 401 sem token, 200 com token, 401 com token expirado |
| Frontend | continua funcionando ([`regressao-frontend-*.png`](evidencias/sprint2)); o histórico exibe o vetor no lugar do Lamport; a mensagem da transferência entre agências agora diz "publicada (entrega assíncrona)" |
| Idempotência por `idOperacao` e histórico por conta (extras do Sprint 1) | mantidos |
| **Justificativa da Parte F do Sprint 1 sobre a chave `X-Internal-Key`** | **superada:** a rota REST `creditar-remoto` e a chave interna foram **removidas** — o crédito entre agências chega por mensageria. A fronteira de confiança agora é a credencial do RabbitMQ (ver Parte C, pergunta 3) |
| Limitação conhecida do Sprint 1 (débito pendurado quando o destino falha) | **em parte resolvida** (destino fora do ar deixou de ser erro; broker fora do ar estorna) e **em parte adiada** (atomicidade real → Sprint 4) |

## Checklist final de entrega (seção 10 do roteiro)

Conferido item a item a partir de um **clone limpo** do repositório (`git clone` → `mvnw package` com os testes → `npm ci && npm run build`):

- [x] RabbitMQ rodando, exchange `iceibank.eventos` (topic) e 3 filas (uma por agência) configuradas — Parte A; [`rabbitmq-manager-filas.png`](evidencias/sprint2/rabbitmq-manager-filas.png)
- [x] Relógio vetorial substituindo o de Lamport, com as três regras — Parte B; `RelogioVetorialService` + testes
- [x] Transferência entre agências publicada como mensagem e consumida de forma assíncrona — [`transferencia-assincrona.png`](evidencias/sprint2/transferencia-assincrona.png)
- [x] Teste de resiliência reproduzido e documentado, **incluindo a conta ausente** (não só o caminho feliz) — [`resiliencia-fila.png`](evidencias/sprint2/resiliencia-fila.png)
- [x] `MesclarLogs` identificando pares concorrentes (e pares causais) — [`linha-do-tempo-causal.png`](evidencias/sprint2/linha-do-tempo-causal.png)
- [x] JWT e frontend do Sprint 1 continuam funcionando (**regressão verificada, não presumida**) — [`regressao-jwt.png`](evidencias/sprint2/regressao-jwt.png), [`regressao-frontend-transferencia.png`](evidencias/sprint2/regressao-frontend-transferencia.png)
- [x] Pelo menos uma funcionalidade adicional, documentada — dead-letter com reprocessamento; [`funcionalidade-adicional.png`](evidencias/sprint2/funcionalidade-adicional.png)
- [x] Pasta `evidencias/sprint2/` com os 3 prints da seção 4.3 (mais o da funcionalidade adicional e os complementares) — [índice](evidencias/sprint2/README.md)
- [x] `RESPOSTAS.md` atualizado com as questões das seções 6.4, 7.5 e 8.3 e a funcionalidade adicional — este documento
- [x] Atividade de recapitulação (Fluxo de Execução) respondida — [`FLUXO-DE-EXECUCAO.md`](FLUXO-DE-EXECUCAO.md)

**Declaração de uso de IA (nota de transparência do roteiro):** usei o Claude (Anthropic) como apoio para rascunhar, implementar, revisar e testar código e texto ao longo do sprint. Todo o código entregue foi executado e verificado por mim nesta máquina — testes automatizados e execuções reais contra um RabbitMQ real — e consigo explicar e defender qualquer trecho.
