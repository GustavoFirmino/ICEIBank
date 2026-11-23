# Roteiro de apresentação — Sprints 1, 2 e 3

Aluno: Gustavo Pessoa Firmino Duarte · Projeto: ICEIBank · Login de demonstração: **gustavo / senha123**.

## Parte I — Apresentando pelos vídeos (plano principal, ≈ 12 min)

Os vídeos foram gravados com os processos rodando de verdade (agências Spring Boot, RabbitMQ, emulador Android) e **não têm áudio**: a legenda queimada explica cada passo. Quem fala é você; as tabelas abaixo dão o minuto exato em que cada legenda aparece e o que dizer.

| Vídeo | Duração | Onde está |
|---|---|---|
| Sprint 1 | — | Release `v1.0-sprint1` no GitHub (anexo do release) |
| Sprint 2 | 04:20 | `videos/ICEIBank-sprint2.mp4` |
| Sprint 3 | 05:20 | `videos/ICEIBank-sprint3.mp4` |

**Sugestão de ritmo:** Sprint 1 (só a ideia: agências + JWT + REST, ~1 min de fala) → Sprint 2 completo → Sprint 3 completo, pausando nos marcos em **negrito** das tabelas para comentar. Depois, perguntas (seção "Perguntas que o professor pode fazer") e, se sobrar tempo, a demo ao vivo (Parte II).

**Dicas:** abra os dois .mp4 em abas/janelas antes de começar; deixe em tela cheia; use a barra de espaço para pausar quando quiser explicar mais do que a legenda diz.

**Falas-chave por sprint**
- **Sprint 2:** "A transferência entre agências deixou de ser chamada REST direta: a origem debita e publica uma mensagem no RabbitMQ; o destino consome de forma assíncrona. O relógio vetorial ordena os eventos; a idempotência (idOperacao) evita crédito duplicado; se o destino cai, a fila retém; se o crédito não pode ser aplicado, vai para a dead-letter e pode ser reprocessado."
- **Sprint 3:** "As agências elegem líder por Bully (maior ID ativo vence), com heartbeat de 3 s e failover automático. O histórico de lideranças é reconstruído dos logs. O app Flutter é offline-first: guarda o vetor da agência, enfileira operações offline e, ao sincronizar, a agência compara vetores — se mudou, responde 409 e o app pergunta o que fazer."

## Tempos e falas de cada vídeo

### Vídeo do Sprint 2

**Cena 1 — terminal, logs das agências e painel do RabbitMQ** (começa em 00:06)

| Tempo | O que aparece / o que dizer |
|---|---|
| 00:06 | Sprint 2: a transferência entre agências deixa de ser uma chamada REST direta e vira uma mensagem assíncrona no RabbitMQ. |
| 00:16 | As 3 agências (Spring Boot, portas 4047 a 4049) estão no ar. À direita: o log de cada uma e o painel ao vivo do RabbitMQ. |
| 00:20 | Topologia: uma exchange "iceibank.eventos" (tipo topic, durável) e uma fila durável por agência. A fila "creditos-mortos" é a dead-letter. |
| 00:35 | Continua do Sprint 1: autenticação por JWT. Fazemos login e usamos o token nas chamadas seguintes. |
| 00:43 | Cada conta pertence a uma só agência (id % 3). A conta 0 fica na Agência 0 e a conta 1 na Agência 1. |
| 00:57 | Transferência de R$ 30 da conta 0 (Agência 0) para a conta 1 (Agência 1): a Agência 0 debita e PUBLICA a mensagem no RabbitMQ. |
| 01:03 | Relógio vetorial: ao ENVIAR, a Agência 0 incrementa a sua posição e anexa o vetor inteiro; ao RECEBER, a Agência 1 faz o máximo posição a posição e incrementa a sua. |
| 01:13 | O débito foi na origem e o crédito chegou ao destino de forma assíncrona (veja TRANSFERENCIA_PUBLICADA → TRANSFERENCIA_CREDITO_REMOTO nos logs). |
| 01:17 | Idempotência: reenviar a MESMA operação (mesmo idOperacao) não debita nem credita de novo. |
| 01:31 | Resiliência: derrubamos a Agência 1 (destino). A transferência continua funcionando — a mensagem fica retida na fila. |
| 01:44 | Resposta 200 mesmo com o destino fora: no painel, a fila-agencia-1 fica sem consumidores (0) e retém a mensagem (1 pronta, assim que o painel atualiza). |
| 01:50 | A Agência 1 volta. As contas ficam só em memória, então a conta 1 não existe mais: o crédito não pode ser aplicado e vai para a dead-letter. |
| 02:10 | Funcionalidade adicional: GET /mensagens-mortas lista os créditos retidos na dead-letter queue, sem perder dinheiro. |
| 02:15 | Recriamos a conta 1 e reprocessamos: a mensagem é republicada e o crédito finalmente é aplicado. |
| 02:36 | Linha do tempo unificada: o MesclarLogs compara os vetores e prova o que é causal (ANTES) e o que é concorrente entre agências. |
| 02:52 | Fim da cena: mensageria assíncrona, relógio vetorial, idempotência, retenção na fila e dead-letter com reprocessamento. |

**Cena 2 — interface do RabbitMQ Manager** (começa em 03:10)

| Tempo | O que aparece / o que dizer |
|---|---|
| 03:10 | A interface do RabbitMQ Manager (a mesma que as agências usam): entramos com a conta de teste local. |
| 03:16 | Aba Exchanges: a exchange "iceibank.eventos" (tipo topic, durável) é o ponto de publicação das agências. |
| 03:22 | Dentro dela, os bindings: a chave agencia.<destino>.creditar leva a mensagem para a fila da agência de destino. |
| 03:36 | Aba Queues: uma fila durável por agência (cada uma com 1 consumidor) e a fila de mensagens mortas (dead-letter). |

**Cena 3 — frontend web (continuidade do Sprint 1)** (começa em 03:49)

| Tempo | O que aparece / o que dizer |
|---|---|
| 03:49 | Continuidade: o frontend web do Sprint 1 continua funcionando. Login na Agência 0. |
| 03:53 | Consulta de saldo da conta 0. |
| 03:56 | Transferência entre agências (0 → 1) pela interface: agora aparece "publicada (entrega assíncrona)". |

*Duração total do vídeo: 04:20 (inclui cartão de abertura de 6 s e de encerramento de 8 s).*

### Vídeo do Sprint 3

**Cena 1 — Bully ao vivo, failover, histórico e sincronização (terminal + logs)** (começa em 00:06)

| Tempo | O que aparece / o que dizer |
|---|---|
| 00:06 | Sprint 3: as 3 agências elegem uma líder entre si com o algoritmo de Bully — a agência ATIVA de maior ID sempre vence. |
| 00:10 | Ao subir, cada agência inicia uma eleição: manda ELEIÇÃO só para quem tem ID MAIOR. Se ninguém responde, ela vira líder e anuncia (COORDENADOR). |
| 00:19 | É normal haver uma "tempestade" de eleições na subida (várias TORNOU_LIDER): o algoritmo sempre converge para o maior ID. |
| 00:32 | As 3 agências concordam: a líder é a Agência 2 (o maior ID). |
| 00:37 | Failover: derrubamos a líder (Agência 2). A cada 3 s cada agência faz um heartbeat na líder; sem resposta, dispara nova eleição sozinha. |
| 00:54 | LIDER_NAO_RESPONDEU → ELEICAO → TORNOU_LIDER: a Agência 1 (maior ID ativo) assume. Nenhuma intervenção manual. |
| 01:04 | Observabilidade: o historico-lideranca reconstrói a linha do tempo de líderes a partir dos logs das 3 agências. |
| 01:19 | A Agência 2 volta: inicia uma eleição ao subir e, por ter o maior ID, retoma a liderança. |
| 01:42 | Parte E: sincronização offline-first. O app guarda o VETOR da agência na última leitura e o devolve junto com a operação pendente. |
| 01:56 | GET /contas/0 agora devolve também o vetorAtual da agência — é o "vetor conhecido" que o app guarda. |
| 02:00 | Enquanto o app estava offline, a conta mudou por outro caminho (um depósito de R$ 10). |
| 02:07 | O app sincroniza um saque pendente com o vetor ANTIGO: a agência detecta que mudou e responde 409, devolvendo o estado atual (não aplica às cegas). |
| 02:23 | Com o vetorAtual devolvido no 409 (o app/usuário decidiu aplicar), a mesma operação é aceita. |
| 02:35 | Resumo da parte A: Bully (eleição e coordenador), heartbeat com failover automático, histórico de lideranças e sincronização com detecção de conflito por vetor. |

**Cena 2 — app Flutter no emulador: login, painel da líder e failover** (começa em 02:54)

| Tempo | O que aparece / o que dizer |
|---|---|
| 02:54 | App Flutter (Parte D): o primeiro cliente mobile do projeto reaproveita a mesma API REST. O painel "Líder atual" já mostra a líder das agências. |
| 02:59 | Login com JWT na Agência de entrada 0. |
| 03:09 | Abrimos a conta 0: saldo R$ 100, vindo da Agência 0 (conta 0 % 3 = agência 0). |
| 03:18 | Failover com o app aberto: derrubamos a líder (Agência 2). Ninguém toca na tela do app. |
| 03:24 | Em poucos segundos as agências reelegem a Agência 1 e o painel do app atualiza sozinho, registrando a troca "Ag. 2 passou para Ag. 1". |

**Cena 3 — app offline-first: modo avião, fila e sincronização** (começa em 03:43)

| Tempo | O que aparece / o que dizer |
|---|---|
| 03:43 | Offline-first (Parte F): colocamos o emulador em modo avião — o app perde a conexão com todas as agências. |
| 03:52 | Depositar R$ 40 offline: a operação não falha. Vai para a fila local e a tela já mostra o saldo PROVISÓRIO (R$ 140) com a marca "pendente". |
| 04:02 | Reconectamos (modo avião desligado). O app detecta a rede e sincroniza sozinho, sem ninguém tocar em nada. |
| 04:11 | Aviso "1 operação sincronizada" e saldo confirmado pela agência: R$ 140. Se o vetor avançou só por causa da eleição, o app repete sozinho (o saldo é o que a pessoa viu). |

**Cena 4 — conflito (409) e resolução no app** (começa em 04:26)

| Tempo | O que aparece / o que dizer |
|---|---|
| 04:26 | Conflito: de novo em modo avião, fazemos um saque pendente de R$ 10... |
| 04:38 | ...e, enquanto o app está offline, a conta muda por outro caminho: um depósito de R$ 7 direto na agência. |
| 04:43 | Ao reconectar, a agência vê que o vetor mudou e responde 409. O app NÃO descarta nem aplica em silêncio: mostra o saldo real e pergunta o que fazer. |
| 04:56 | "Aplicar mesmo assim": o app reenvia com o vetor atual devolvido no 409 e a operação é aceita. |

*Duração total do vídeo: 05:20 (inclui cartão de abertura de 6 s e de encerramento de 8 s).*

---

## Parte II — Demonstração ao vivo (opcional / plano B do vídeo)

### 0. O que deve estar no ar
- 3 janelas do PowerShell das agências (portas 4047, 4048, 4049) — a líder é a **2**.
- RabbitMQ (http://localhost:15672, guest/guest), emulador Android com o app e o frontend web (http://localhost:5173).
- Subir tudo do zero: `iniciar-rabbitmq-sem-docker.ps1` (ou `iniciar-rabbitmq.ps1` com Docker), `iniciar-agencias.ps1`, emulador `iceibank_pixel`, `npm.cmd run dev` em `frontend/`.

### 1. Bully (Partes A–B) — 3 min
1. Mostre as janelas das agências: `ELEICAO_INICIADA` / `TORNOU_LIDER` / `LIDER_ANUNCIADO` na subida (a "tempestade" de eleições) e o resultado único: **líder = 2**.
2. No app (emulador) o painel "Líder atual" mostra **Agência 2**.
3. **Feche a janela da Agência 2.** Em ~3 s as agências 0 e 1 registram `LIDER_NAO_RESPONDEU`, elegem a **1** e o painel do app muda sozinho, com "Ag. 2 passou para Ag. 1". Ninguém tocou no app.
4. Rode `.\historico-lideranca.ps1` para a linha do tempo de líderes.

### 2. Offline-first e vetor (Partes E–F) — 5 min
1. No app: login → conta **0** → saldo R$ 100.
2. **Modo avião** (barra de notificações do emulador, ou `adb shell cmd connectivity airplane-mode enable`).
3. Deposite 40: aparece **"Modo offline"**, **saldo provisório R$ 140** e a operação **"pendente"**.
4. Desligue o modo avião: o app **sincroniza sozinho** ("1 operação sincronizada", saldo confirmado 140).
5. **Conflito:** modo avião de novo → saque de 10 (pendente) → por fora, deposite 7 na conta 0 (`POST /contas/0/depositar` ou pelo frontend web) → desligue o modo avião: o backend responde **409** e o app mostra **"Conflito… Saldo real agora…"** com "Aplicar mesmo assim" / "Descartar".
6. Explique: o app guarda o `vetorAtual` da agência; ao sincronizar devolve esse vetor; se o vetor da agência avançou → 409 (não aplica às cegas). Se o saldo for o mesmo que a pessoa viu (o vetor andou só por uma eleição), o app repete sozinho (falso conflito).

### 3. Continuidade (Sprints 1–2) — 1 min
- Frontend web: login, saldo, **transferência entre agências 0 → 1** → "publicada (entrega assíncrona)"; confira no RabbitMQ (fila-agencia-1: 1 publicada, 1 entregue).

## Perguntas que o professor pode fazer (respostas em `RESPOSTAS.md`)
- Por que o maior ID vence? Por que várias `TORNOU_LIDER` na subida? (5.5)
- O que acontece se o heartbeat for muito curto/longo? E se a rede estiver lenta? (6.4)
- Por que comparar o vetor e não o saldo? Por que qualquer evento é conflito? (9.6)
- Riscos da UI otimista num app bancário? (10.3)

## Se algo travar
- App no emulador demora ~20–30 s no primeiro arranque (APK de debug).
- Se a líder não aparecer no app: confira se as 3 janelas das agências estão abertas e se o emulador enxerga `10.0.2.2`.
- O JWT dura **5 minutos**: se o app voltar ao login, entre de novo (a fila offline continua guardada).
- Contas ficam só em memória: ao reiniciar uma agência, recrie as contas (`POST /contas`).
