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
# Pre-requisitos: JDK 17 ou superior no PATH (java -version) e, desde o Sprint 2, um RabbitMQ
# acessivel: .\iniciar-rabbitmq.ps1 (Docker) ou a variavel RABBITMQ_URL apontando para o CloudAMQP.
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$agencia = Join-Path $raiz "agencia"
$jar = Join-Path $agencia "target\agencia-1.0.0.jar"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "ERRO: 'java' nao encontrado no PATH. Instale um JDK 17+ e abra um novo terminal." -ForegroundColor Red
    exit 1
}

# reempacota se o jar nao existe OU se algum fonte/pom e mais novo que ele (evita subir uma versao antiga do codigo)
$precisaEmpacotar = -not (Test-Path $jar)
if (-not $precisaEmpacotar) {
    $jarData = (Get-Item $jar).LastWriteTime
    $maisNovo = Get-ChildItem -Path (Join-Path $agencia "src"), (Join-Path $agencia "pom.xml") -Recurse -File |
        Where-Object { $_.LastWriteTime -gt $jarData } | Select-Object -First 1
    $precisaEmpacotar = $null -ne $maisNovo
}

if ($precisaEmpacotar) {
    Write-Host "Empacotando com o Maven Wrapper (primeira vez pode demorar: baixa o Maven e as dependencias)..."
    Push-Location $agencia
    try {
        # JDKs recentes fazem o Maven imprimir avisos no stderr; com "Stop" o PowerShell 5.1 trataria
        # isso como erro e abortaria - so o codigo de saida do mvnw importa.
        $ErrorActionPreference = "Continue"
        & .\mvnw.cmd -q -DskipTests package
        $codigo = $LASTEXITCODE
        $ErrorActionPreference = "Stop"
        if ($codigo -ne 0) { throw "Falha ao empacotar (mvnw retornou $codigo)." }
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
