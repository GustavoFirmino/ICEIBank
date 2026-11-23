// Monta o video final do Sprint 3 a partir das cenas gravadas por sprint3a.js e sprint3b.js
const { montar, VID } = require('./montagem');
const fs = require('fs');
const path = require('path');
const tem = (f) => fs.existsSync(path.join(VID, f));
const seg = [
  { cartao: [
    { t: 'ICEIBank', tam: 130, negrito: true, cor: '0x60a5fa' },
    { t: 'Sprint 3 — Desenvolvimento móvel e consenso', tam: 64, negrito: true },
    { t: 'Eleição de líder (Bully), app Flutter e modo offline-first', tam: 42, cor: '0x94a3b8' },
    { t: 'Gustavo Pessoa Firmino Duarte', tam: 34, cor: '0xd6deeb' },
    { t: 'Laboratório de Desenvolvimento de Aplicações Móveis e Distribuídas', tam: 30, cor: '0x94a3b8' },
  ], dur: 6 },
  { mp4: 'sprint3-cena1-bully.mp4', srt: 'sprint3-cena1-bully.srt' },
];
for (const c of ['2a', '2b', '2c']) {
  if (tem(`s3b${c[1] === 'a' ? 1 : c[1] === 'b' ? 2 : 3}.mp4`)) {
    seg.push({ emu: `s3b${c[1] === 'a' ? 1 : c[1] === 'b' ? 2 : 3}.mp4`, logs: `sprint3-cena${c}-logs.mp4`, srt: `sprint3-cena${c}.srt`, deslocEmu: 0.7 });
  }
}
seg.push({ cartao: [
  { t: 'Sprint 3 — o que foi entregue', tam: 64, negrito: true, cor: '0x60a5fa' },
  { t: '• Algoritmo de Bully nas 3 agências, com heartbeat e failover automático', tam: 36 },
  { t: '• Histórico de lideranças reconstruído a partir dos logs', tam: 36 },
  { t: '• Sincronização offline-first no backend: conflito detectado pelo relógio vetorial (409)', tam: 36 },
  { t: '• App Flutter: fluxo bancário, painel da líder ao vivo e modo offline-first', tam: 36 },
  { t: 'github.com/GustavoFirmino/ICEIBank', tam: 32, cor: '0x94a3b8' },
], dur: 8 });
const dur = montar(path.join(VID, 'ICEIBank-sprint3.mp4'), seg);
console.log('video final do Sprint 3:', dur.toFixed(1) + ' s');
