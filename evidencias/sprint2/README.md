# Evidências — Sprint 2 (ICEIBank)

Prints gerados a partir da **saída real** dos comandos, executados contra as 3 agências e um RabbitMQ real (Docker), com `Get-Date` visível. Nomes de arquivo:

- `transferencia-assincrona.png` — transferência entre agências via mensageria, com o log das duas agências
- `resiliencia-fila.png` — agência de destino derrubada, transferência publicada mesmo assim, e o que acontece quando ela volta (conta ausente → dead-letter)
- `linha-do-tempo-causal.png` — saída do `MesclarLogs`: pares concorrentes × pares causais
- `funcionalidade-adicional.png` — dead-letter queue (fila de mensagens não processadas) no RabbitMQ
- `regressao-jwt-frontend.png` — JWT e frontend do Sprint 1 continuam funcionando
