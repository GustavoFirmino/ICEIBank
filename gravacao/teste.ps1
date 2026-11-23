# Teste de fumaca da gravacao: abre um console, grava 8 s do desktop e restaura tudo.
. (Join-Path $PSScriptRoot "lib.ps1")
$saida = Join-Path $Raiz "videos\teste-gravacao.mp4"
try {
    Console-Fonte 20
    Minimizar-Tudo
    $h = Abrir-Console "ICEIBank - teste" (Join-Path $PSScriptRoot "_dummy.ps1")
    Posicionar $h 0 0 820 740
    Pausa 1
    Iniciar-Gravacao $saida
    Pausa 8
    Parar-Gravacao
    Get-Process | Where-Object { $_.MainWindowTitle -like "*ICEIBank - teste*" } | Stop-Process -Force
}
finally {
    Console-Fonte-Restaurar
    Restaurar-Tudo
}
Write-Host "gravado: $saida"
