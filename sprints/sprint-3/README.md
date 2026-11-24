# Sprint 3 — Desenvolvimento móvel e consenso (Bully)

| Pasta | O que tem | Arquivos principais |
|---|---|---|
| `1-bully-heartbeat-failover` | Eleição de Bully, mensagens ELEIÇÃO/COORDENADOR, heartbeat e reeleição | `BullyService`, `BullyController`, `BullyProperties` |
| `2-historico-de-liderancas` | Linha do tempo de líderes reconstruída dos logs | `HistoricoLideranca` |
| `3-offline-first-backend` | `POST /contas/{id}/sincronizar`, comparação de vetores e 409 | `ContaService`, `ContasController`, `SincronizarRequest`, `ConflitoSincronizacaoException`, `Vetores` |
| `4-app-flutter-offline-first` | App: fila de pendentes, UI otimista, sincronização automática, cache Hive, tela de conflito, painel da líder | `app_state.dart`, `local_store.dart`, `api_client.dart`, `leader_service.dart`, `conta_screen.dart` |
