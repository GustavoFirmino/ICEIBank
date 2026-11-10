# Aluno: Gustavo Pessoa Firmino Duarte
# Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
# Projeto: ICEIBank - Sprint 3 - Parte C
# OFFSET pessoal (2 ultimos digitos da matricula): 47
#
# Roda o HistoricoLideranca (linha do tempo de eleicoes/lideres a partir dos logs
# das 3 agencias) direto do .jar empacotado, como o linha-do-tempo.ps1.
#
# Uso (a partir da raiz do repositorio):  .\historico-lideranca.ps1
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$agencia = Join-Path $raiz "agencia"
$jar = Join-Path $agencia "target\agencia-1.0.0.jar"

if (-not (Test-Path $jar)) {
    Write-Host "Jar ainda nao existe - empacotando com o Maven Wrapper..."
    Push-Location $agencia
    try {
        $ErrorActionPreference = "Continue"
        & .\mvnw.cmd -q -DskipTests package 2>$null
    } finally { $ErrorActionPreference = "Stop"; Pop-Location }
}

Push-Location $agencia
try {
    & java -cp $jar "-Dloader.main=br.pucminas.labdamd.iceibank.agencia.ferramentas.HistoricoLideranca" org.springframework.boot.loader.launch.PropertiesLauncher
} finally {
    Pop-Location
}
