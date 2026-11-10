# ICEIBank — app mobile (Sprint 3)

Cliente Flutter do ICEIBank: fluxo bancário completo (login JWT, saldo, depósito, saque, transferência), **painel da líder atual** (Bully) atualizado sozinho e modo **offline-first** (cache local, fila de operações pendentes, UI otimista e sincronização automática com detecção de conflito por relógio vetorial).

Consome a mesma API REST das 3 agências do backend (`../agencia`, Java/Spring) — o app é um cliente a mais, não substitui nada.

## Como rodar

Pré-requisitos: Flutter SDK, as 3 agências no ar (`..\iniciar-agencias.ps1`) e, para o fluxo web, JDK 17+.

```bash
flutter pub get
flutter test          # 13 testes do modo offline-first e do painel da líder
flutter analyze
```

**Emulador Android** (as agências estão no host da máquina, que o emulador enxerga como `10.0.2.2` — já é o padrão no app):

```bash
flutter run
```

**Celular físico** (use o IP da máquina na rede; o celular e o PC precisam estar na mesma rede):

```bash
flutter run --dart-define=API_HOST=192.168.0.10
```

**Chrome (web)** — a porta **5173** é obrigatória, porque é a única origem liberada no CORS das agências:

```bash
flutter run -d chrome --web-port 5173
```

Login de demonstração (definido em `application.yml` das agências): `gustavo` / `senha123`. Para ter contas, crie-as pelo frontend web ou pela API (`POST /contas`); o número da conta define a agência dona dela (`id % 3`).

## Estrutura

| Arquivo | Papel |
|---|---|
| `lib/api_client.dart` | único ponto que fala HTTP; anexa o JWT e converte todo erro em `ApiException` (status 0 = sem conexão) |
| `lib/leader_service.dart` | polling de `GET /bully/lider` a cada 3 s; tenta outra agência se uma não responde; guarda as últimas trocas de líder |
| `lib/app_state.dart` | regras e estado: operar, enfileirar, sincronizar, resolver conflito |
| `lib/local_store.dart` | armazenamento local (Hive) e versão em memória para testes |
| `lib/screens/`, `lib/widgets/` | login, tela principal, tela da conta e painel da líder |
| `test/offline_first_test.dart` | testes com um "servidor falso" que imita o vetor e o 409 do backend |

## Como o offline-first funciona

1. **Leitura:** o saldo vem do cache local e é atualizado quando a agência responde; cada leitura guarda também o `vetorAtual` da agência.
2. **Escrita sem conexão:** depósito/saque vai para a fila com o vetor conhecido; a tela mostra o **saldo provisório** e a marca "pendente". Saque maior que o saldo provisório é recusado na hora. Transferência exige conexão.
3. **Sincronização automática:** ao detectar rede de volta (`connectivity_plus`) — e a cada 5 s enquanto houver fila — o app envia `POST /contas/{id}/sincronizar` com o vetor conhecido. Operações em sequência encadeiam no vetor devolvido pela anterior.
4. **Conflito (HTTP 409):** a agência recusa quando o vetor dela avançou. O app **não descarta nem reaplica em silêncio**: marca a operação como conflito, atualiza o cache com o estado real e pergunta (Aplicar mesmo assim / Descartar). Exceção: se o saldo real é igual ao que a pessoa viu, o app repete uma vez sozinho (o vetor pode ter avançado só por uma eleição ou por outra conta).
5. **Sessão expirada (401, o JWT dura 5 min):** o app volta ao login e a fila continua guardada.

## Limitações conhecidas

- Testado em Chrome (web) e em testes automatizados; **não** executado em emulador/dispositivo Android nesta máquina (sem Android SDK).
- O polling da líder não pausa em segundo plano.
- Offline só para depósito e saque (o endpoint de sincronização do backend não cobre transferência).
- O vetor é por agência, não por conta: qualquer evento na agência pode gerar um 409 (mitigado pela repetição automática quando o saldo não mudou).
