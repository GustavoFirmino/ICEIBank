# Aluno: Gustavo Pessoa Firmino Duarte
# Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
# Projeto: ICEIBank - Sprint 2 - Parte A (RabbitMQ)
# OFFSET pessoal (2 ultimos digitos da matricula): 47
#
# Sobe um RabbitMQ local (com o painel de administracao) em um container
# Docker, de forma idempotente: se o container ja existe, so garante que esta
# rodando. Alternativa ao CloudAMQP (roteiro, secao 4.1) - a aplicacao le a
# URL de RABBITMQ_URL e, sem a variavel, usa amqp://guest:guest@localhost:5672.
#
#   AMQP:    localhost:5672
#   Painel:  http://localhost:15672   (usuario guest / senha guest)
#
# Uso (a partir da raiz do repositorio):  .\iniciar-rabbitmq.ps1
$ErrorActionPreference = "Stop"
$nome = "rabbitmq-iceibank"
$volume = "rabbitmq-iceibank-dados"
$imagem = "rabbitmq:4-management"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "ERRO: Docker nao encontrado. Instale o Docker Desktop, ou use o CloudAMQP (defina RABBITMQ_URL)." -ForegroundColor Red
    exit 1
}

# o daemon do Docker Desktop precisa estar rodando
$null = & docker info 2>$null
if ($LASTEXITCODE -ne 0) {
    $dd = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dd) {
        Write-Host "Docker Desktop parado - iniciando (pode levar ~30 s)..."
        Start-Process $dd
        $t = 0
        do { Start-Sleep -Seconds 3; $t += 3; $null = & docker info 2>$null } while ($LASTEXITCODE -ne 0 -and $t -lt 180)
    }
    if ($LASTEXITCODE -ne 0) { Write-Host "ERRO: o daemon do Docker nao respondeu. Abra o Docker Desktop e rode de novo." -ForegroundColor Red; exit 1 }
}

$existe = (& docker ps -a --filter "name=^$nome$" --format "{{.Names}}")
if (-not $existe) {
    Write-Host "Criando o container $nome ($imagem)..."
    & docker volume create $volume | Out-Null
    # Em alguns ambientes Windows/WSL o volume nasce com dono root e o RabbitMQ cai com
    # "eacces" ao ler .erlang.cookie - ajustar o dono antes de subir resolve.
    & docker run --rm -v "${volume}:/var/lib/rabbitmq" --user root --entrypoint chown $imagem -R rabbitmq:rabbitmq /var/lib/rabbitmq | Out-Null
    & docker run -d --name $nome -p 5672:5672 -p 15672:15672 -v "${volume}:/var/lib/rabbitmq" $imagem | Out-Null
} else {
    & docker start $nome | Out-Null
}

Write-Host "Aguardando o RabbitMQ aceitar conexoes..."
$t = 0
do { Start-Sleep -Seconds 2; $t += 2; & docker exec $nome rabbitmq-diagnostics -q ping 2>$null | Out-Null } while ($LASTEXITCODE -ne 0 -and $t -lt 120)
if ($LASTEXITCODE -ne 0) { Write-Host "ERRO: RabbitMQ nao respondeu em 120 s. Veja: docker logs $nome" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "RabbitMQ no ar." -ForegroundColor Green
Write-Host "  AMQP:   amqp://guest:guest@localhost:5672/   (padrao da aplicacao)"
Write-Host "  Painel: http://localhost:15672   (guest / guest)"
