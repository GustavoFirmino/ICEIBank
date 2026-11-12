# Aluno: Gustavo Pessoa Firmino Duarte
# Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
# Projeto: ICEIBank - Sprint 3 (alternativa ao iniciar-rabbitmq.ps1 quando NAO ha Docker)
# OFFSET pessoal (2 ultimos digitos da matricula): 47
#
# Sobe um RabbitMQ local SEM Docker e SEM instalador/administrador, a partir de dois .zip oficiais
# extraidos em uma pasta do usuario (padrao: %USERPROFILE%\tools\rabbit):
#   erlang\    -> https://github.com/erlang/otp/releases           (otp_win64_28.x.zip)
#   rabbitmq\  -> https://github.com/rabbitmq/rabbitmq-server/releases (rabbitmq-server-windows-4.x.zip)
# Habilita o painel de gerenciamento (http://localhost:15672, guest/guest) e deixa o broker em primeiro plano
# (Ctrl+C para parar). As agencias usam amqp://guest:guest@localhost:5672/ por padrao - nada a configurar.
#
# Uso:  powershell -NoProfile -ExecutionPolicy Bypass -File .\iniciar-rabbitmq-sem-docker.ps1
param([string]$Pasta = (Join-Path $env:USERPROFILE "tools\rabbit"))
$ErrorActionPreference = "Stop"
$erlang = Join-Path $Pasta "erlang"
$rabbit = Get-ChildItem (Join-Path $Pasta "rabbitmq") -Directory -Filter "rabbitmq_server-*" | Select-Object -First 1
if (-not (Test-Path (Join-Path $erlang "bin\erl.exe")) -or $null -eq $rabbit) {
    throw "Erlang/RabbitMQ nao encontrados em $Pasta (veja os links no cabecalho deste script)."
}
$env:ERLANG_HOME = $erlang
$env:Path = (Join-Path $erlang "bin") + ";" + $env:Path
$sbin = Join-Path $rabbit.FullName "sbin"
& (Join-Path $sbin "rabbitmq-plugins.bat") enable rabbitmq_management
& (Join-Path $sbin "rabbitmq-server.bat")
