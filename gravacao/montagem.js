// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - videos de apresentacao (montagem com ffmpeg)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Monta o video final: cartoes de titulo/encerramento, cada cena normalizada para 1920x1080 a 15 quadros/s com
// a legenda (.srt) queimada, e a juncao de tudo. Sem audio (as legendas explicam o que acontece).
'use strict';
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { FFBIN, RAIZ } = require('./motor');

const VID = path.join(RAIZ, 'videos');
const FONTE = '_fonte.ttf';
const FONTE_B = '_fonte-b.ttf';
// fontes copiadas para a pasta de trabalho: evita o escape de 'C:' dentro do filtergraph do ffmpeg
for (const [de, para] of [['segoeui.ttf', '_fonte.ttf'], ['segoeuib.ttf', '_fonte-b.ttf']]) { const d = path.join(VID, para); if (!fs.existsSync(d)) fs.copyFileSync('C:/Windows/Fonts/' + de, d); }
const BASE = ['-hide_banner', '-loglevel', 'error', '-y'];

function ff(args) { execFileSync(path.join(FFBIN, 'ffmpeg.exe'), [...BASE, ...args], { cwd: VID, stdio: 'inherit' }); }
function ffprobeDur(arq) {
  const o = execFileSync(path.join(FFBIN, 'ffprobe.exe'), ['-v', 'error', '-show_entries', 'format=duration', '-of', 'default=nw=1:nk=1', arq], { cwd: VID }).toString();
  return parseFloat(o);
}

// obs.: no filtro "subtitles" o tamanho/margens do estilo sao relativos a uma altura de 288 (PlayResY padrao do SRT),
// entao 8.5 equivale a ~32 px em video 1080p
const ESTILO = "FontName=Segoe UI,FontSize=8.5,Bold=1,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,BackColour=&HC0000000,BorderStyle=3,Outline=1.6,Shadow=0,MarginV=8,MarginL=24,MarginR=24,Alignment=2";

// Cartao de titulo/encerramento: linhas = [{ t:'texto', tam:60, cor:'white', negrito:true }]
function cartao(saida, linhas, dur = 5) {
  let y = 380 - (linhas.length - 1) * 40;
  const filtros = ['color=c=0x0b0f14:s=1920x1080:r=15:d=' + dur];
  const partes = [];
  linhas.forEach((l, i) => {
    const arq = `_cartao_${path.basename(saida, '.mp4')}_${i}.txt`;
    fs.writeFileSync(path.join(VID, arq), l.t, 'utf8');
    const tam = l.tam || 48;
    partes.push(`drawtext=fontfile=${l.negrito ? FONTE_B : FONTE}:textfile=${arq}:fontsize=${tam}:fontcolor=${l.cor || 'white'}:x=(w-text_w)/2:y=${y}`);
    y += tam + 34;
  });
  const vf = filtros[0] + ',' + partes.join(',') + ',fade=t=in:st=0:d=0.6,fade=t=out:st=' + (dur - 0.6) + ':d=0.6,format=yuv420p';
  ff(['-f', 'lavfi', '-i', vf, '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '20', '-t', String(dur), saida]);
}

// Normaliza uma cena (1920x1080, 15 fps) e queima a legenda, se houver
function cena(saida, mp4, srt) {
  let vf = 'scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=0x0b0f14,fps=15';
  if (srt) vf += `,subtitles=${srt}:original_size=1920x1080:force_style='${ESTILO}'`;
  vf += ',format=yuv420p';
  ff(['-i', mp4, '-vf', vf, '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '22', '-an', saida]);
}

// Emulador (retrato, 540x1200) a esquerda + painel de logs (1434x1080) a direita; legenda so sobre a coluna direita.
// deslocEmu: segundos a descartar do inicio do video do emulador (o screenrecord comeca um pouco antes dos logs)
function composicao(saida, emu, logs, srt, deslocEmu = 0.7) {
  const estilo = ESTILO.replace('MarginL=24', 'MarginL=136');
  const fc = `[0:v]scale=486:1080,fps=15,setsar=1[a];[1:v]fps=15,scale=1434:1080,setsar=1[b];[a][b]hstack=inputs=2:shortest=1` +
    (srt ? `,subtitles=${srt}:force_style='${estilo}'` : '') + ',format=yuv420p[v]';
  ff(['-ss', String(deslocEmu), '-i', emu, '-i', logs, '-filter_complex', fc, '-map', '[v]', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '22', '-an', saida]);
}

// Junta uma lista de segmentos [{ mp4, srt }], [{ emu, logs, srt }] ou [{ cartao: [...linhas], dur }] no arquivo final
function montar(final, segmentos) {
  const parciais = [];
  segmentos.forEach((s, i) => {
    const p = `_seg${String(i).padStart(2, '0')}.mp4`;
    if (s.cartao) cartao(p, s.cartao, s.dur || 5);
    else if (s.emu) composicao(p, s.emu, s.logs, s.srt, s.deslocEmu);
    else cena(p, s.mp4, s.srt);
    parciais.push(p);
  });
  fs.writeFileSync(path.join(VID, '_lista.txt'), parciais.map((p) => `file '${p}'`).join('\n'));
  ff(['-f', 'concat', '-safe', '0', '-i', '_lista.txt', '-c', 'copy', '-movflags', '+faststart', final]);
  parciais.forEach((p) => fs.rmSync(path.join(VID, p), { force: true }));
  fs.readdirSync(VID).filter((f) => f.startsWith('_cartao_') || f === '_lista.txt').forEach((f) => fs.rmSync(path.join(VID, f), { force: true }));
  return ffprobeDur(final);
}

module.exports = { montar, cartao, cena, ffprobeDur, VID };
