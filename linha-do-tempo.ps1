# Aluno: Gustavo Pessoa Firmino Duarte
# Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
# Projeto: ICEIBank - Sprint 1 - Parte E
# OFFSET pessoal (2 ultimos digitos da matricula): 47
#
# Roda o MesclarLogs (linha do tempo unificada das 3 agencias, ordenada por
# relogio de Lamport) direto do .jar empacotado, sem passar pelo Maven -
# mais rapido e sem os avisos de JVM que o Maven imprime em JDKs recentes.
#
# Uso (a partir da raiz do repositorio):  .\linha-do-tempo.ps1
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$agencia = Join-Path $raiz "agencia"
$jar = Join-Path $agencia "target\agencia-1.0.0.jar"

if (-not (Test-Path $jar)) {
    Write-Host "Jar ainda nao existe - empacotando com o Maven Wrapper..."
    Push-Location $agencia
    try { & .\mvnw.cmd -q -DskipTests package } finally { Pop-Location }
}

# PropertiesLauncher e o launcher do Spring Boot que aceita trocar a classe
# principal do .jar (loader.main) - assim o MesclarLogs roda com o mesmo
# classpath da aplicacao (Jackson etc.) sem precisar do exec-maven-plugin.
Push-Location $agencia
try {
    & java -cp $jar "-Dloader.main=br.pucminas.labdamd.iceibank.agencia.ferramentas.MesclarLogs" org.springframework.boot.loader.launch.PropertiesLauncher
} finally {
    Pop-Location
}
