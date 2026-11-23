// Lista, para cada video final, o instante (mm:ss) em que cada legenda aparece - base do roteiro de fala.
// Uso: node gravacao/roteiro-tempos.js
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { FFBIN, RAIZ } = require('./motor');
const VID = path.join(RAIZ, 'videos');
const dur = (f) => parseFloat(execFileSync(path.join(FFBIN, 'ffprobe.exe'), ['-v', 'error', '-show_entries', 'format=duration', '-of', 'default=nw=1:nk=1', path.join(VID, f)]).toString());
const lerSrt = (f) => fs.readFileSync(path.join(VID, f), 'utf8').trim().split(/\r?\n\r?\n/).map((b) => {
  const l = b.split(/\r?\n/); const m = /(\d+):(\d+):(\d+),(\d+)/.exec(l[1]);
  return { t: (+m[1]) * 3600 + (+m[2]) * 60 + (+m[3]) + (+m[4]) / 1000, texto: l.slice(2).join(' ') };
});
const mmss = (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(Math.floor(s % 60)).padStart(2, '0')}`;

function tabela(titulo, cartaoIni, cenas) {
  let off = cartaoIni; const linhas = [];
  for (const c of cenas) {
    linhas.push(`\n**${c.nome}** (começa em ${mmss(off)})\n\n| Tempo | O que aparece / o que dizer |\n|---|---|`);
    for (const e of lerSrt(c.srt)) linhas.push(`| ${mmss(off + e.t)} | ${e.texto} |`);
    off += c.dur;
  }
  return `### ${titulo}\n` + linhas.join('\n') + `\n\n*Duração total do vídeo: ${mmss(off + 8)} (inclui cartão de abertura de 6 s e de encerramento de 8 s).*\n`;
}

const s2 = [
  { nome: 'Cena 1 — terminal, logs das agências e painel do RabbitMQ', srt: 'sprint2-cena1-terminal.srt', dur: dur('sprint2-cena1-terminal.mp4') },
  { nome: 'Cena 2 — interface do RabbitMQ Manager', srt: 'sprint2-cena2-rabbitmq-ui.srt', dur: dur('sprint2-cena2-rabbitmq-ui.mp4') },
  { nome: 'Cena 3 — frontend web (continuidade do Sprint 1)', srt: 'sprint2-cena3-frontend.srt', dur: dur('sprint2-cena3-frontend.mp4') },
];
const comp = (emu, logs) => Math.min(dur(emu) - 0.7, dur(logs));
const s3 = [
  { nome: 'Cena 1 — Bully ao vivo, failover, histórico e sincronização (terminal + logs)', srt: 'sprint3-cena1-bully.srt', dur: dur('sprint3-cena1-bully.mp4') },
  { nome: 'Cena 2 — app Flutter no emulador: login, painel da líder e failover', srt: 'sprint3-cena2a.srt', dur: comp('s3b1.mp4', 'sprint3-cena2a-logs.mp4') },
  { nome: 'Cena 3 — app offline-first: modo avião, fila e sincronização', srt: 'sprint3-cena2b.srt', dur: comp('s3b2.mp4', 'sprint3-cena2b-logs.mp4') },
  { nome: 'Cena 4 — conflito (409) e resolução no app', srt: 'sprint3-cena2c.srt', dur: comp('s3b3.mp4', 'sprint3-cena2c-logs.mp4') },
];
const md = tabela('Vídeo do Sprint 2', 6, s2) + '\n' + tabela('Vídeo do Sprint 3', 6, s3);
fs.writeFileSync(path.join(VID, '_tempos.md'), md, 'utf8');
console.log(md);
