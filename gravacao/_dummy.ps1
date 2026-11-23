1..12 | ForEach-Object { Write-Host ("Linha de teste {0} - ICEIBank ({1})" -f $_, (Get-Date -Format "HH:mm:ss")); Start-Sleep -Milliseconds 600 }
Write-Host "fim do teste" -ForegroundColor Green
Start-Sleep -Seconds 3
