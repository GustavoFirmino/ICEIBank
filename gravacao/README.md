# gravacao/ — ferramentas para gravar os vídeos de apresentação

Scripts Node que executam o projeto de verdade (agências, RabbitMQ, emulador), gravam a tela por capturas sucessivas (headless Chrome) e montam os vídeos finais com ffmpeg, com legenda queimada e sem áudio.

## Pré-requisitos
- Java 17 + `agencia/target/agencia-1.0.0.jar` compilado (`mvn package`), Node, ffmpeg (caminho em `motor.js`, `FFBIN`), Google Chrome.
- `cd gravacao; npm.cmd install` (puppeteer-core).
- RabbitMQ no ar: `..\iniciar-rabbitmq-sem-docker.ps1` (ou Docker).
- Sprint 3 (app): emulador `iceibank_pixel` aberto com o app instalado; `adb` no PATH. Coordenadas de toque em `coordenadas.json`.
- Rode os scripts num terminal normal do usuário (o Java/Chrome precisam de rede local irrestrita).

## Comandos
```powershell
node gravacao\sprint2.js            # grava as cenas do Sprint 2 (--cenas=1,2,3)
node gravacao\montar-sprint2.js     # videos\ICEIBank-sprint2.mp4
node gravacao\sprint3a.js           # Bully, failover, historico, sincronizacao 409
node gravacao\sprint3b.js           # app no emulador (--partes=1,2,3)
node gravacao\montar-sprint3.js     # videos\ICEIBank-sprint3.mp4
node gravacao\roteiro-tempos.js     # tabela de tempos das legendas (videos\_tempos.md)
```

## Arquivos
`motor.js` (gravação, execução de comandos, logs ao vivo) · `terminal.html` (tela de gravação) · `montagem.js` (cartões, legendas, junção) · `folha.js` (contact sheet para conferir) · `_agencias.js` (sobe as agências para calibrar toques).
