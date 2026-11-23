. (Join-Path $PSScriptRoot "lib.ps1")
$saida = Join-Path $Raiz "videos\bench.txt"
$r = @()
$r += "== ddagrab (4 s)"
$r += (& $script:FFmpeg -hide_banner -loglevel error -stats -f lavfi -i "ddagrab=framerate=15:draw_mouse=1,hwdownload,format=bgra" -t 4 -f null - 2>&1 | Out-String).Split("`r")[-2]
$r += "== ddagrab output_idx=0 + scale (4 s)"
$r += (& $script:FFmpeg -hide_banner -loglevel error -stats -f lavfi -i "ddagrab=framerate=15:output_idx=0,hwdownload,format=bgra,scale=1280:800" -t 4 -f null - 2>&1 | Out-String).Split("`r")[-2]
$r += "== gdigrab regiao 1280x800 (4 s)"
$r += (& $script:FFmpeg -hide_banner -loglevel error -stats -f gdigrab -framerate 15 -offset_x 0 -offset_y 0 -video_size 1280x800 -t 4 -i desktop -f null - 2>&1 | Out-String).Split("`r")[-2]
$r | Set-Content $saida -Encoding UTF8
