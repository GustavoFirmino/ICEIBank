# Sprint 2 — Mensageria (RabbitMQ) e relógio vetorial

| Pasta | O que tem | Arquivos principais |
|---|---|---|
| `1-mensageria-rabbitmq` | Exchange topic, filas duráveis por agência, dead-letter, publicar com confirmação, consumir, reprocessar; URL de acesso ao RabbitMQ no `application.yml` (`RABBITMQ_URL`) | `MensageriaConfig`, `RabbitPublicadorCreditos`, `CreditoRemotoConsumer`, `MensagensMortasService` |
| `2-relogio-vetorial` | Vetor por agência (ao enviar/receber) e comparação causal x concorrente | `RelogioVetorialService`, `Vetores`, `RelacaoCausal` |
| `3-transferencia-assincrona-e-idempotencia` | Débito na origem, publicação do crédito, estorno se o broker cair | `TransferenciaService`, `IdempotencyStore` |
| `4-logs-e-linha-do-tempo` | `MesclarLogs` com pares concorrentes e verificação de causalidade | `EventLogService`, `MesclarLogs` |
