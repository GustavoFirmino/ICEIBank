# Evidências — Sprint 2 (ICEIBank)

Prints gerados a partir da **saída real** dos comandos, executados contra as 3 agências e um **RabbitMQ real** (Docker), com `Get-Date` visível; os do navegador são capturas do painel do RabbitMQ e do frontend rodando de verdade. Nada foi editado ou montado à mão (o prompt `PS C:\ICEIBank>` dos prints de terminal é só um rótulo — os comandos, respostas e linhas de log são os reais).

## Exigidas pelo roteiro (seção 4.3)

| Arquivo | O que mostra |
|---|---|
| `transferencia-assincrona.png` | Topologia criada no RabbitMQ (exchange topic + 3 filas duráveis + dead-letter), transferência entre agências publicada por uma agência e creditada pela outra, com o **vetor** de cada evento no log das duas |
| `resiliencia-fila.png` | Agência de destino derrubada → transferência ainda responde 200 → mensagem **retida** na fila (1 pronta, 0 consumidores) → agência volta **sem a conta** (memória) → crédito rejeitado → mensagem na dead-letter queue, com os dados intactos |
| `linha-do-tempo-causal.png` | Saída do `MesclarLogs`: pares **concorrentes** entre agências diferentes × par **causal** envio → recebimento verificado como `ANTES` (e ausente da lista de concorrentes) |
| `funcionalidade-adicional.png` | Dead-letter queue com inspeção e reprocessamento: `GET /mensagens-mortas`, `POST /mensagens-mortas/reprocessar` sem a conta (volta para a fila), depois com a conta recriada (saldo 500 → 700, fila vazia) |

## Complementares

| Arquivo | O que mostra |
|---|---|
| `entrega-duplicada.png` | A mesma mensagem publicada 2× direto no RabbitMQ creditou 1× (consumidor idempotente por `idMensagem`) |
| `ordem-invertida.png` | Experimento da pergunta 7 do Fluxo de Execução: uma transferência que **aconteceu primeiro** foi **aplicada depois** (parou na dead-letter); as duas publicações são provadamente concorrentes |
| `broker-fora-do-ar.png` | Broker parado → **503** com o débito **estornado**; broker volta → o **mesmo** `idOperacao` funciona |
| `regressao-jwt.png` | JWT do Sprint 1 continua funcionando (sem token 401 / válido 200 / expirado 401) e a rota REST `creditar-remoto` não existe mais (404) |
| `regressao-frontend-transferencia.png` | Frontend do Sprint 1: transferência entre agências pela interface, agora "publicada (entrega assíncrona)" |
| `regressao-frontend-saldo-destino.png` | O crédito chegou de forma assíncrona: saldo da conta de destino consultado pelo frontend na Agência 1 |
| `rabbitmq-manager-filas.png` | Painel **RabbitMQ Manager**: as 4 filas duráveis (`D`) com `DLX`/`DLK` |
| `rabbitmq-manager-exchange.png` | Painel **RabbitMQ Manager**: exchange `iceibank.eventos` (topic, durável) |
