# Aluno: Gustavo Pessoa Firmino Duarte
# Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
# Projeto: ICEIBank - videos de apresentacao (Sprints 2 e 3)
# OFFSET pessoal (2 ultimos digitos da matricula): 47
#
# Biblioteca de gravacao de tela: posiciona janelas, grava o desktop com ffmpeg (sem audio) e registra
# as legendas (com o instante em que cada uma aparece) para depois queima-las no video.
# Todos os scripts desta pasta sao ASCII; os textos das legendas ficam em arquivos .txt UTF-8.

$ErrorActionPreference = "Stop"
$script:Raiz = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$script:FFmpeg = (Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter ffmpeg.exe -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
$script:Adb = Join-Path $env:USERPROFILE "tools\android-sdk\platform-tools\adb.exe"

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win {
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  public struct RECT { public int Left, Top, Right, Bottom; }
}
"@

$script:Legendas = @{}       # id -> texto (carregado de um .txt UTF-8 "id|texto")
$script:Eventos = New-Object System.Collections.ArrayList   # @{t=segundos; texto=...; dur=...}
$script:T0 = $null
$script:ProcFFmpeg = $null

function Carregar-Legendas($arquivo) {
    foreach ($l in (Get-Content -Path $arquivo -Encoding UTF8)) {
        if ($l.Trim() -eq "" -or $l.StartsWith("#")) { continue }
        $p = $l.Split("|", 2)
        $script:Legendas[$p[0].Trim()] = $p[1].Trim()
    }
}

# Mostra uma legenda a partir de AGORA, por $seg segundos (o texto vem do arquivo de legendas)
function Legenda($id, $seg = 6) {
    if ($null -eq $script:T0) { return }
    $texto = $script:Legendas[$id]
    if (-not $texto) { throw "legenda '$id' nao existe" }
    $t = ((Get-Date) - $script:T0).TotalSeconds
    [void]$script:Eventos.Add(@{ t = $t; dur = $seg; texto = $texto })
}

function Pausa($s) { Start-Sleep -Milliseconds ([int]($s * 1000)) }

function Achar-Janela($trecho, $tentativas = 40) {
    for ($i = 0; $i -lt $tentativas; $i++) {
        $p = Get-Process | Where-Object { $_.MainWindowHandle -ne [IntPtr]::Zero -and $_.MainWindowTitle -like "*$trecho*" } | Select-Object -First 1
        if ($p) { return $p.MainWindowHandle }
        Start-Sleep -Milliseconds 500
    }
    throw "janela '$trecho' nao apareceu"
}

# x,y,w,h em pixels LOGICOS (a tela e 1280x800 logicos = 1920x1200 fisicos)
function Posicionar($handle, $x, $y, $w, $h, [switch]$SemTopmost) {
    [void][Win]::ShowWindow($handle, 9)            # SW_RESTORE
    $after = [IntPtr]::new(-1)                      # HWND_TOPMOST
    if ($SemTopmost) { $after = [IntPtr]::new(-2) }
    [void][Win]::SetWindowPos($handle, $after, [int]($x * 1.5), [int]($y * 1.5), [int]($w * 1.5), [int]($h * 1.5), 0x0040)
}

# Abre uma janela de console classico (conhost) com titulo, fonte grande e executa o script dado
function Abrir-Console($titulo, $arquivoPs1, $args2 = "") {
    $cmd = "powershell -NoProfile -ExecutionPolicy Bypass -NoExit -Command `"`$Host.UI.RawUI.WindowTitle='$titulo'; & '$arquivoPs1' $args2`""
    Start-Process conhost.exe -ArgumentList $cmd | Out-Null
    return (Achar-Janela $titulo)
}

# Fonte do console (restaurada no fim): tamanho em pixels
function Console-Fonte($px) {
    $k = "HKCU:\Console"
    if ($null -eq $script:FonteAntiga) { $script:FonteAntiga = (Get-ItemProperty $k).FontSize }
    Set-ItemProperty -Path $k -Name FontSize -Value ($px * 65536)
    Set-ItemProperty -Path $k -Name FaceName -Value "Consolas"
}
function Console-Fonte-Restaurar() {
    if ($null -ne $script:FonteAntiga) { Set-ItemProperty -Path "HKCU:\Console" -Name FontSize -Value $script:FonteAntiga }
}

function Minimizar-Tudo() { (New-Object -ComObject Shell.Application).MinimizeAll(); Start-Sleep -Seconds 1 }
function Restaurar-Tudo() { (New-Object -ComObject Shell.Application).UndoMinimizeAll() }

# Inicia a gravacao do desktop (sem audio). Saida: $arquivo (mp4 H.264).
function Iniciar-Gravacao($arquivo) {
    if (Test-Path $arquivo) { Remove-Item $arquivo -Force }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $script:FFmpeg
    $psi.Arguments = "-hide_banner -loglevel error -f gdigrab -framerate 15 -draw_mouse 1 -i desktop -c:v libx264 -preset ultrafast -crf 23 -pix_fmt yuv420p -y `"$arquivo`""
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.CreateNoWindow = $true
    $script:ProcFFmpeg = [System.Diagnostics.Process]::Start($psi)
    # espera o primeiro quadro (arquivo aparecer) e marca T0
    for ($i = 0; $i -lt 40 -and -not (Test-Path $arquivo); $i++) { Start-Sleep -Milliseconds 250 }
    Start-Sleep -Milliseconds 700
    $script:T0 = Get-Date
    $script:Eventos.Clear()
}

function Parar-Gravacao() {
    if ($null -eq $script:ProcFFmpeg) { return }
    # ffmpeg encerra limpo (fecha o mp4) ao receber 'q' no stdin
    $script:ProcFFmpeg.StandardInput.WriteLine("q")
    $script:ProcFFmpeg.StandardInput.Flush()
    if (-not $script:ProcFFmpeg.WaitForExit(30000)) { $script:ProcFFmpeg.Kill() }
    $script:ProcFFmpeg = $null
}

# Converte os eventos em .srt (UTF-8 sem BOM). $deslocamento corrige a latencia de inicio do gdigrab.
function Salvar-Srt($arquivo, $deslocamento = 0.0) {
    $sb = New-Object System.Text.StringBuilder
    $n = 1
    foreach ($e in $script:Eventos) {
        $ini = [Math]::Max(0, $e.t - $deslocamento)
        $fim = $ini + $e.dur
        $sb.AppendLine("$n") | Out-Null
        $sb.AppendLine((Tempo-Srt $ini) + " --> " + (Tempo-Srt $fim)) | Out-Null
        $sb.AppendLine($e.texto) | Out-Null
        $sb.AppendLine("") | Out-Null
        $n++
    }
    [IO.File]::WriteAllText($arquivo, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
}
function Tempo-Srt($s) {
    $ts = [TimeSpan]::FromSeconds($s)
    return ("{0:00}:{1:00}:{2:00},{3:000}" -f [int]$ts.TotalHours, $ts.Minutes, $ts.Seconds, $ts.Milliseconds)
}
