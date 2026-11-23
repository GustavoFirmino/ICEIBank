// Gera uma "folha de contato" (3x3) com quadros espalhados de um video, para conferir a gravacao.
// Uso: node gravacao/folha.js <video.mp4> [saida.png]
const path = require('path');
const fs = require('fs');
const { execFileSync } = require('child_process');
const { FFBIN, RAIZ } = require('./motor');
const arq = path.resolve(process.argv[2]);
const saida = path.resolve(process.argv[3] || path.join(RAIZ, 'videos', 'folha.png'));
const ff = path.join(FFBIN, 'ffmpeg.exe');
const dur = parseFloat(execFileSync(path.join(FFBIN, 'ffprobe.exe'), ['-v', 'error', '-show_entries', 'format=duration', '-of', 'default=nw=1:nk=1', arq]).toString());
const tmp = path.join(RAIZ, 'videos', '_folha'); fs.rmSync(tmp, { recursive: true, force: true }); fs.mkdirSync(tmp, { recursive: true });
const fr = [0.05, 0.17, 0.29, 0.41, 0.53, 0.65, 0.77, 0.88, 0.97];
fr.forEach((f, i) => execFileSync(ff, ['-hide_banner', '-loglevel', 'error', '-ss', String((dur * f).toFixed(2)), '-i', arq, '-frames:v', '1', '-vf', 'scale=640:-1', '-y', path.join(tmp, `f${i + 1}.png`)]));
execFileSync(ff, ['-hide_banner', '-loglevel', 'error', '-i', path.join(tmp, 'f%d.png'), '-filter_complex', 'tile=3x3', '-frames:v', '1', '-y', saida]);
fs.rmSync(tmp, { recursive: true, force: true });
console.log('duracao ' + dur.toFixed(1) + 's -> ' + saida);
