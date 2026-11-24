# Sprint 1 — API REST/MVC + frontend (relógio de Lamport)

| Pasta | O que tem | Arquivos principais |
|---|---|---|
| `1-autenticacao-jwt` | Login, geração/validação do JWT, filtro que protege as rotas, rotas públicas, segredo e expiração (`application.yml`) | `AuthController`, `JwtService`, `JwtAuthFilter`, `SecurityConfig`, `UsuarioStore` |
| `2-contas-e-particionamento` | REST de contas (depositar, sacar, histórico) e a partição `id % 3` | `ContasController`, `ContaService`, `AgenciaProperties` |
| `3-relogio-de-lamport` | Relógio lógico de Lamport (evento local, ao enviar, ao receber) | `LamportClockService` |
| `4-transferencia-e-idempotencia` | Transferência local e entre agências; mesmo `idOperacao` executa uma vez só | `TransferenciaService`, `IdempotencyStore` |
| `5-logs-e-linha-do-tempo` | Log de eventos e o `MesclarLogs` (ordem por Lamport) | `EventLogService`, `MesclarLogs` |
| `6-frontend-react` | Chamadas à API, com o `Authorization: Bearer` | `httpClient.js`, `authApi.js`, `contasApi.js` |
