# Aluno: Gustavo Pessoa Firmino Duarte
# Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
# Projeto: ICEIBank - Sprint 1
# OFFSET pessoal (2 ultimos digitos da matricula): 47
#
# Sobe as 3 agencias, cada uma em uma janela propria do PowerShell (para os
# logs de cada agencia ficarem visiveis separadamente, como o roteiro pede).
# Nao precisa de Maven instalado: usa o Maven Wrapper (mvnw.cmd) para gerar
# o .jar na primeira vez, e roda com "java -jar" (que funciona mesmo em
# caminhos com acento, diferente do "mvn spring-boot:run").
#
# Uso (a partir da raiz do repositorio):
#   .\iniciar-agencias.ps1
#
# Pre-requisito: JDK 17 ou superior no PATH (confira com: java -version).
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$agencia = Join-Path $raiz "agencia"
$jar = Join-Path $agencia "target\agencia-1.0.0.jar"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "ERRO: 'java' nao encontrado no PATH. Instale um JDK 17+ e abra um novo terminal." -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $jar)) {
    Write-Host "Jar ainda nao existe - empacotando com o Maven Wrapper (primeira vez pode demorar: baixa o Maven e as dependencias)..."
    Push-Location $agencia
    try {
        & .\mvnw.cmd -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Falha ao empacotar (mvnw retornou $LASTEXITCODE)." }
    } finally {
        Pop-Location
    }
}

foreach ($id in 0, 1, 2) {
    $porta = 4047 + $id
    $comando = "`$Host.UI.RawUI.WindowTitle = 'ICEIBank - Agencia $id (porta $porta)'; Set-Location '$agencia'; java -jar target\agencia-1.0.0.jar --spring.profiles.active=agencia$id"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $comando
    Write-Host "Agencia $id iniciada em uma nova janela (porta $porta)."
}

Write-Host ""
Write-Host "Aguarde as 3 janelas mostrarem 'Started AgenciaApplication'. Depois:"
Write-Host "  - API:      http://localhost:4047, :4048, :4049 (login: gustavo / senha123)"
Write-Host "  - Frontend: cd frontend; npm install; npm run dev  ->  http://localhost:5173"
