# Evidências — Sprint 3

| Arquivo | O que mostra | Como foi gerado |
|---|---|---|
| `eleicao-inicial.png` | as 3 agências subindo e convergindo para a mesma líder (`/bully/lider`) | `demo-sprint3.ps1` (saída real dos comandos, desenhada em PNG; `.txt` com o texto exato) |
| `failover.png` | a líder (agência 2) derrubada e a nova eleição automática (líder passa a ser a 1) | `demo-sprint3.ps1` |
| `lider-volta.png` | a agência 2 volta, elege-se de novo e as 3 concordam | `demo-sprint3.ps1` (extra) |
| `historico-lideranca.png` | saída do `HistoricoLideranca` (linha do tempo de líderes) | `demo-sprint3.ps1` |
| `conflito-sincronizacao.png` | `POST /contas/{id}/sincronizar` com vetor antigo → 409; depois com o vetor atual → aplicado | `demo-sprint3.ps1` |
| `app-lider-atual.png` | app Flutter mostrando a líder atual | **emulador Android** (Pixel 6, Android 35), APK debug, agências reais em 10.0.2.2 |
| `app-failover-antes.png` / `app-failover.png` | painel antes e depois da queda da líder, sem tocar na tela (aparece "Ag. 2 passou para Ag. 1") | idem |
| `app-offline.png` | **modo avião do Android** (✈ na barra): depósito vira operação "pendente", saldo provisório | idem |
| `app-sincronizacao.png` | rede de volta: o app sincroniza sozinho ("1 operação sincronizada") | idem |
| `app-conflito.png` | 409 tratado de forma visível: "Conflito… Saldo real agora…" com as opções | idem |
| `web-chrome/app-*.png` | os mesmos 6 prints do app compilado para web no Chrome (evidência complementar) | Chrome automatizado (Puppeteer) |
| `regressao-mensageria.png` (+ `.txt`) | regressão Sprints 1–2 com **RabbitMQ real**: JWT, contas, transferência local e entre agências, idempotência, filas | `regressao.sh` contra as 3 agências + RabbitMQ 4.4 (sem Docker) |
| `regressao-frontend-saldo.png` / `regressao-frontend-transferencia.png` | frontend web do Sprint 1 funcionando (login, saldo, transferência entre agências) | Chrome automatizado contra as agências |

Detalhes de como cada print foi gerado: seção "Como os prints do app foram gerados" em [`../../RESPOSTAS.md`](../../RESPOSTAS.md).
