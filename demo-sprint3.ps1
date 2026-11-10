# Aluno: Gustavo Pessoa Firmino Duarte
# Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
# Projeto: ICEIBank - Sprint 3
# OFFSET pessoal (2 ultimos digitos da matricula): 47
#
# Roda os cenarios REAIS do backend do Sprint 3 (eleicao inicial, conflito de sincronizacao,
# failover da lider, volta da lider, historico de liderancas) e salva, em evidencias\sprint3\:
#   - um .txt com a saida exata de cada cenario (com Get-Date visivel), e
#   - um .png com essa mesma saida desenhada em fundo de terminal.
# Sobe as 3 agencias sozinho (processos ocultos, logs em evidencias\sprint3\logs-agencias\) e as derruba no fim.
# Nao precisa de RabbitMQ: o Bully e a sincronizacao nao dependem do broker (as agencias apenas
# reclamam da conexao AMQP nos logs).
#
# Uso (a partir da raiz do repositorio):  .\demo-sprint3.ps1
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$agencia = Join-Path $raiz "agencia"
$jar = Join-Path $agencia "target\agencia-1.0.0.jar"
$saida = Join-Path $raiz "evidencias\sprint3"
$logsAg = Join-Path $saida "logs-agencias"
New-Item -ItemType Directory -Force -Path $saida, $logsAg | Out-Null

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    # JDK recem-instalado (winget) que ainda nao entrou no PATH desta janela
    $jdkDir = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $jdkDir) { throw "java nao encontrado no PATH (precisa de JDK 17+)." }
    $env:JAVA_HOME = $jdkDir.FullName
    $env:Path = (Join-Path $jdkDir.FullName "bin") + ";" + $env:Path
}
$javaExe = (Get-Command java).Source

# empacota se preciso
$precisa = -not (Test-Path $jar)
if (-not $precisa) {
    $jd = (Get-Item $jar).LastWriteTime
    $precisa = $null -ne (Get-ChildItem -Path (Join-Path $agencia "src"), (Join-Path $agencia "pom.xml") -Recurse -File | Where-Object { $_.LastWriteTime -gt $jd } | Select-Object -First 1)
}
if ($precisa) {
    Write-Host "Empacotando..."
    Push-Location $agencia
    try { $ErrorActionPreference = "Continue"; & .\mvnw.cmd -q -DskipTests package 2>$null; $c = $LASTEXITCODE; $ErrorActionPreference = "Stop"; if ($c -ne 0) { throw "mvnw falhou ($c)" } } finally { Pop-Location }
}

# logs antigos (sprints anteriores) sairiam misturados no historico: guarda uma copia e limpa
$dados = Join-Path $agencia "data"
New-Item -ItemType Directory -Force -Path $dados | Out-Null
$antigos = Get-ChildItem $dados -Filter "agencia-*.jsonl" -ErrorAction SilentlyContinue
if ($antigos) {
    $bk = Join-Path $dados ("backup-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
    New-Item -ItemType Directory -Force -Path $bk | Out-Null
    $antigos | Move-Item -Destination $bk
}

# ---- desenha linhas de texto num PNG com cara de terminal -------------------------------------
function Salvar-Png($linhas, $caminho) {
    $fonte = New-Object System.Drawing.Font("Consolas", 13)
    $tmp = New-Object System.Drawing.Bitmap 10, 10
    $g0 = [System.Drawing.Graphics]::FromImage($tmp)
    $alt = [int]$fonte.GetHeight($g0) + 2
    $larg = 400
    foreach ($l in $linhas) { $w = [int]$g0.MeasureString([string]$l, $fonte, 4000, [System.Drawing.StringFormat]::GenericTypographic).Width; if ($w -gt $larg) { $larg = $w } }
    $larg += 32; $altura = ($linhas.Count * $alt) + 32
    $bmp = New-Object System.Drawing.Bitmap $larg, $altura
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::FromArgb(12, 12, 12))
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    $pincel = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(204, 204, 204))
    $verde = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(97, 214, 214))
    $y = 16
    foreach ($l in $linhas) {
        $p = $pincel
        if (([string]$l).StartsWith("PS ")) { $p = $verde }
        $g.DrawString([string]$l, $fonte, $p, 16, $y, [System.Drawing.StringFormat]::GenericTypographic)
        $y += $alt
    }
    $bmp.Save($caminho, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $g0.Dispose(); $bmp.Dispose(); $tmp.Dispose()
}

$script:cena = New-Object System.Collections.ArrayList
function Cmd($texto) { [void]$script:cena.Add("PS " + $raiz + "> " + $texto) }
function Linha($texto) { [void]$script:cena.Add([string]$texto) }
function Mostrar-Hora() { Cmd "Get-Date"; Linha ((Get-Date).ToString("dddd, dd 'de' MMMM 'de' yyyy HH:mm:ss")) }
function Fechar-Cena($nome) {
    Set-Content -Path (Join-Path $saida ($nome + ".txt")) -Value $script:cena -Encoding UTF8
    Salvar-Png $script:cena (Join-Path $saida ($nome + ".png"))
    Write-Host ("  -> evidencias\sprint3\" + $nome + ".png")
    $script:cena = New-Object System.Collections.ArrayList
}

# ---- agencias ---------------------------------------------------------------------------------
$procs = @{}
function Subir-Agencia($id) {
    $ts = Get-Date -Format "HHmmss"
    $p = Start-Process -FilePath $javaExe -ArgumentList "-jar", "target\agencia-1.0.0.jar", "--spring.profiles.active=agencia$id" `
        -WorkingDirectory $agencia -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logsAg "agencia-$id-$ts.out.log") -RedirectStandardError (Join-Path $logsAg "agencia-$id-$ts.err.log")
    $script:procs[$id] = $p
}
function Lider($id) {
    try { return (Invoke-RestMethod -Uri ("http://localhost:" + (4047 + $id) + "/bully/lider") -TimeoutSec 2) } catch { return $null }
}
function Esperar-No-Ar($ids, $max = 120) {
    $fim = (Get-Date).AddSeconds($max)
    while ((Get-Date) -lt $fim) {
        $ok = $true
        foreach ($i in $ids) { if ($null -eq (Lider $i)) { $ok = $false } }
        if ($ok) { return }
        Start-Sleep -Seconds 1
    }
    throw "agencias nao subiram a tempo"
}
function Mostrar-Lideres($ids) {
    foreach ($i in $ids) {
        $porta = 4047 + $i
        Cmd ('Invoke-RestMethod -Uri "http://localhost:' + $porta + '/bully/lider" -Method Get')
        $r = Lider $i
        if ($null -eq $r) { Linha "(agencia $i nao respondeu - fora do ar)" } else { Linha ("idAgencia liderAtual"); Linha ("---------- ----------"); Linha ("{0,10} {1,10}" -f $r.idAgencia, $r.liderAtual) }
    }
}
function Parar-Tudo() { foreach ($k in @($script:procs.Keys)) { try { Stop-Process -Id $script:procs[$k].Id -Force -ErrorAction SilentlyContinue } catch {} } }

try {
    Write-Host "1/5 Subindo as 3 agencias (eleicao inicial)..."
    foreach ($i in 0, 1, 2) { Subir-Agencia $i }
    Esperar-No-Ar @(0, 1, 2)
    Start-Sleep -Seconds 8   # a "tempestade" de eleicoes converge

    Mostrar-Hora
    Mostrar-Lideres @(0, 1, 2)
    Fechar-Cena "eleicao-inicial"

    Write-Host "2/5 Conflito de sincronizacao (409)..."
    $b = @{ username = "gustavo"; password = "senha123" } | ConvertTo-Json
    $tok = (Invoke-RestMethod -Uri "http://localhost:4047/auth/login" -Method Post -ContentType "application/json" -Body $b).token
    $h = @{ Authorization = "Bearer $tok" }
    $uri = "http://localhost:4047/contas"
    Mostrar-Hora
    Cmd '$h = @{ Authorization = "Bearer <jwt do login>" }'
    Cmd 'Invoke-RestMethod -Uri "http://localhost:4047/contas" -Method Post -Headers $h -ContentType "application/json" -Body ''{"id":0,"titular":"Gustavo","saldoInicial":100}'''
    $c = Invoke-RestMethod -Uri $uri -Method Post -Headers $h -ContentType "application/json" -Body '{"id":0,"titular":"Gustavo","saldoInicial":100}'
    Linha ($c | ConvertTo-Json -Compress)
    Cmd 'Invoke-RestMethod -Uri "http://localhost:4047/contas/0" -Headers $h      # o app guarda o vetorAtual'
    $s1 = Invoke-RestMethod -Uri "$uri/0" -Headers $h
    Linha ($s1 | ConvertTo-Json -Compress)
    $vAntigo = @($s1.vetorAtual)
    Cmd 'Invoke-RestMethod -Uri "http://localhost:4047/contas/0/depositar" -Method Post -Headers $h -ContentType "application/json" -Body ''{"valor":10}''   # mudanca "enquanto o app estava offline"'
    $d = Invoke-RestMethod -Uri "$uri/0/depositar" -Method Post -Headers $h -ContentType "application/json" -Body '{"valor":10}'
    Linha ($d | ConvertTo-Json -Compress)
    $corpo = @{ tipo = "SAQUE"; valor = 30; vetorConhecido = $vAntigo } | ConvertTo-Json -Compress
    Cmd ('Invoke-WebRequest -Uri "http://localhost:4047/contas/0/sincronizar" -Method Post -Headers $h -ContentType "application/json" -Body ''' + $corpo + '''   # vetor ANTIGO')
    $vAtual = $null
    try {
        Invoke-WebRequest -Uri "$uri/0/sincronizar" -Method Post -Headers $h -ContentType "application/json" -Body $corpo -UseBasicParsing | Out-Null
        Linha "ERRO: deveria ter sido recusado"
    } catch {
        $resp = $_.Exception.Response
        $txt = (New-Object System.IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
        Linha ("HTTP " + [int]$resp.StatusCode + " " + $resp.StatusDescription)
        Linha $txt
        $vAtual = @(($txt | ConvertFrom-Json).vetorAtual)
    }
    $corpo2 = @{ tipo = "SAQUE"; valor = 30; vetorConhecido = $vAtual } | ConvertTo-Json -Compress
    Cmd ('Invoke-RestMethod -Uri "http://localhost:4047/contas/0/sincronizar" -Method Post -Headers $h -ContentType "application/json" -Body ''' + $corpo2 + '''   # vetorAtual devolvido no 409')
    $ok = Invoke-RestMethod -Uri "$uri/0/sincronizar" -Method Post -Headers $h -ContentType "application/json" -Body $corpo2
    Linha ($ok | ConvertTo-Json -Compress)
    Fechar-Cena "conflito-sincronizacao"

    Write-Host "3/5 Failover: derrubando a lider (agencia 2)..."
    Mostrar-Hora
    Mostrar-Lideres @(0, 1, 2)
    Cmd "Stop-Process -Id <processo da agencia 2> -Force    # derruba a lider"
    Stop-Process -Id $procs[2].Id -Force
    Mostrar-Hora
    Start-Sleep -Seconds 10   # heartbeat (3 s) + timeout (1 s) + eleicao
    Cmd "Get-Date    # 10 segundos depois"; Linha ((Get-Date).ToString("dddd, dd 'de' MMMM 'de' yyyy HH:mm:ss"))
    Mostrar-Lideres @(0, 1)
    Fechar-Cena "failover"

    Write-Host "4/5 A agencia 2 volta..."
    Subir-Agencia 2
    Esperar-No-Ar @(2)
    Start-Sleep -Seconds 8
    Mostrar-Hora
    Mostrar-Lideres @(0, 1, 2)
    Fechar-Cena "lider-volta"

    Write-Host "5/5 Historico de liderancas..."
    Push-Location $agencia
    try {
        $ErrorActionPreference = "Continue"
        $hist = & $javaExe -cp $jar "-Dloader.main=br.pucminas.labdamd.iceibank.agencia.ferramentas.HistoricoLideranca" org.springframework.boot.loader.launch.PropertiesLauncher 2>&1 | ForEach-Object { [string]$_ }
        $ErrorActionPreference = "Stop"
    } finally { Pop-Location }
    Mostrar-Hora
    Cmd ".\historico-lideranca.ps1"
    foreach ($l in $hist) { Linha $l }
    Fechar-Cena "historico-lideranca"
}
finally {
    Parar-Tudo
}
Write-Host "Pronto. Prints e .txt em evidencias\sprint3\."
