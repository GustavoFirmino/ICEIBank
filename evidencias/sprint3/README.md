# Evidências — Sprint 3

| Arquivo | O que mostra | Como foi gerado |
|---|---|---|
| `eleicao-inicial.png` | as 3 agências subindo e convergindo para a mesma líder (`/bully/lider`) | `demo-sprint3.ps1` (saída real dos comandos, desenhada em PNG; `.txt` com o texto exato) |
| `failover.png` | a líder (agência 2) derrubada e a nova eleição automática (líder passa a ser a 1) | `demo-sprint3.ps1` |
| `lider-volta.png` | a agência 2 volta, elege-se de novo e as 3 concordam | `demo-sprint3.ps1` (extra) |
| `historico-lideranca.png` | saída do `HistoricoLideranca` (linha do tempo de líderes) | `demo-sprint3.ps1` |
| `conflito-sincronizacao.png` | `POST /contas/{id}/sincronizar` com vetor antigo → 409; depois com o vetor atual → aplicado | `demo-sprint3.ps1` |
| `app-lider-atual.png` | app Flutter mostrando a líder atual | app (web, Chrome 412×860) contra as agências reais |
| `app-failover-antes.png` / `app-failover.png` | painel antes e depois da queda da líder, sem tocar na tela | idem |
| `app-offline.png` | modo avião: depósito vira operação "pendente", saldo provisório | idem (offline emulado no Chrome) |
| `app-sincronizacao.png` | rede de volta: o app sincroniza sozinho ("1 operação sincronizada") | idem |
| `app-conflito.png` | 409 tratado de forma visível: "Conflito… Saldo real agora…" com as opções | idem |

Os prints do app foram feitos com o app **compilado para web** (sem Android SDK nesta máquina); ver a seção "Como os prints do app foram gerados" em [`../../RESPOSTAS.md`](../../RESPOSTAS.md).
