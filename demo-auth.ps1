# Aluno: Gustavo Pessoa Firmino Duarte
# Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
# Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
# OFFSET pessoal (2 ultimos digitos da matricula): 47
#
# Demonstra, de uma vez, os 3 cenarios exigidos pela Parte F contra a
# Agencia 0 (porta 4047), mostrando o status HTTP e o corpo de cada resposta:
#   (a) sem token           -> 401
#   (b) com token valido    -> 200
#   (c) com token expirado  -> 401  (token gerado com 1 s de vida, via o
#                                    parametro de debug ttlOverrideSeconds)
#
# Uso (a partir da raiz do repositorio):  .\demo-auth.ps1
$ErrorActionPreference = "Continue"
$base = "http://localhost:4047"

function Mostrar([string]$titulo, [scriptblock]$chamada) {
    Write-Host ""
    Write-Host ("== " + $titulo) -ForegroundColor Yellow
    try {
        $r = & $chamada
        Write-Host "HTTP 200" -ForegroundColor Green
        Write-Host ($r | ConvertTo-Json -Compress)
    } catch {
        $resp = $_.Exception.Response
        $codigo = if ($resp) { [int]$resp.StatusCode } else { "?" }
        $corpo = ""
        if ($resp) {
            try {
                $s = $resp.GetResponseStream(); if ($s.CanSeek) { $s.Position = 0 }
                $corpo = (New-Object System.IO.StreamReader($s, [System.Text.Encoding]::UTF8)).ReadToEnd()
            } catch { }
        }
        Write-Host ("HTTP " + $codigo) -ForegroundColor Red
        if ($corpo) { Write-Host $corpo } else { Write-Host $_.Exception.Message }
    }
}

Mostrar "(a) GET /contas/0 SEM token" {
    Invoke-RestMethod -Uri "$base/contas/0" -ErrorAction Stop
}

$login = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"gustavo","password":"senha123"}'
$token = $login.token
Write-Host ""
Write-Host ("login ok - token (inicio): " + $token.Substring(0, 30) + "...  expira em " + $login.expiraEmSegundos + " s")

Mostrar "(b) GET /contas/0 COM token valido" {
    Invoke-RestMethod -Uri "$base/contas/0" -Headers @{ Authorization = "Bearer $token" } -ErrorAction Stop
}

$curto = (Invoke-RestMethod -Uri "$base/auth/login?ttlOverrideSeconds=1" -Method Post -ContentType "application/json" -Body '{"username":"gustavo","password":"senha123"}').token
Write-Host ""
Write-Host "token de 1 segundo gerado; esperando 3 s para ele expirar..."
Start-Sleep -Seconds 3

Mostrar "(c) GET /contas/0 com token EXPIRADO" {
    Invoke-RestMethod -Uri "$base/contas/0" -Headers @{ Authorization = "Bearer $curto" } -ErrorAction Stop
}
Write-Host ""
