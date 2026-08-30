# Evidências — Sprint 1 (ICEIBank)

Prints de tela reais (não só código), com a saída de `Get-Date` visível em algum terminal para comprovar execução recente. Nomes de arquivo esperados:

- `transferencia-local.png` — uma transferência dentro da mesma agência
- `transferencia-entre-agencias.png` — uma transferência entre agências diferentes, incluindo os logs das duas agências envolvidas
- `falha-conhecida.png` — a agência de destino derrubada no meio de uma transferência, mostrando a resposta 502 e o log de `TRANSFERENCIA_FALHOU`
- `linha-do-tempo.png` — a saída do `MesclarLogs`
- `funcionalidade-adicional.png` — evidência da idempotência e/ou do histórico de transações
- `auth-sem-token.png` — requisição sem token (401)
- `auth-com-token.png` — requisição com token válido (sucesso)
- `auth-token-expirado.png` — requisição com token expirado (401)
- `frontend-login.png` — tela de login funcionando
- `frontend-transferencia.png` — transferência feita pela interface
- `frontend-erro.png` — erro tratado visivelmente na interface (ex.: saldo insuficiente)
