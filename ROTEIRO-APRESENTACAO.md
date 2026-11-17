# Roteiro de apresentação — Sprint 3 (≈ 10 min)

Login de demonstração: **gustavo / senha123**. Contas: **0** Gustavo (R$ 100, agência 0), **1** Aluno (R$ 50, ag. 1), **2** Carla (R$ 80, ag. 2), **3** Ana (R$ 0, ag. 0).
O número da conta define a agência dona dela (`id % 3`).

## 0. O que deve estar no ar
- 3 janelas do PowerShell das agências (portas 4047, 4048, 4049) — a líder é a **2**.
- RabbitMQ (http://localhost:15672, guest/guest), emulador Android com o app e o frontend web (http://localhost:5173).
- Subir tudo do zero: `iniciar-rabbitmq-sem-docker.ps1` (ou `iniciar-rabbitmq.ps1` com Docker), `iniciar-agencias.ps1`, emulador `iceibank_pixel`, `npm.cmd run dev` em `frontend/`.

## 1. Bully (Partes A–B) — 3 min
1. Mostre as janelas das agências: `ELEICAO_INICIADA` / `TORNOU_LIDER` / `LIDER_ANUNCIADO` na subida (a "tempestade" de eleições) e o resultado único: **líder = 2**.
2. No app (emulador) o painel "Líder atual" mostra **Agência 2**.
3. **Feche a janela da Agência 2.** Em ~3 s as agências 0 e 1 registram `LIDER_NAO_RESPONDEU`, elegem a **1** e o painel do app muda sozinho, com "Ag. 2 passou para Ag. 1". Ninguém tocou no app.
4. Rode `.\historico-lideranca.ps1` para a linha do tempo de líderes.

## 2. Offline-first e vetor (Partes E–F) — 5 min
1. No app: login → conta **0** → saldo R$ 100.
2. **Modo avião** (barra de notificações do emulador, ou `adb shell cmd connectivity airplane-mode enable`).
3. Deposite 40: aparece **"Modo offline"**, **saldo provisório R$ 140** e a operação **"pendente"**.
4. Desligue o modo avião: o app **sincroniza sozinho** ("1 operação sincronizada", saldo confirmado 140).
5. **Conflito:** modo avião de novo → saque de 10 (pendente) → por fora, deposite 7 na conta 0 (`POST /contas/0/depositar` ou pelo frontend web) → desligue o modo avião: o backend responde **409** e o app mostra **"Conflito… Saldo real agora…"** com "Aplicar mesmo assim" / "Descartar".
6. Explique: o app guarda o `vetorAtual` da agência; ao sincronizar devolve esse vetor; se o vetor da agência avançou → 409 (não aplica às cegas). Se o saldo for o mesmo que a pessoa viu (o vetor andou só por uma eleição), o app repete sozinho (falso conflito).

## 3. Continuidade (Sprints 1–2) — 1 min
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
