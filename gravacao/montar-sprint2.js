// Monta o video final do Sprint 2 a partir das cenas gravadas por sprint2.js
const { montar, VID } = require('./montagem');
const path = require('path');
const dur = montar(path.join(VID, 'ICEIBank-sprint2.mp4'), [
  { cartao: [
    { t: 'ICEIBank', tam: 130, negrito: true, cor: '0x60a5fa' },
    { t: 'Sprint 2 — Comunicação indireta', tam: 64, negrito: true },
    { t: 'Mensageria com RabbitMQ (Pub/Sub) e relógio vetorial', tam: 42, cor: '0x94a3b8' },
    { t: 'Gustavo Pessoa Firmino Duarte', tam: 34, cor: '0xd6deeb' },
    { t: 'Laboratório de Desenvolvimento de Aplicações Móveis e Distribuídas', tam: 30, cor: '0x94a3b8' },
  ], dur: 6 },
  { mp4: 'sprint2-cena1-terminal.mp4', srt: 'sprint2-cena1-terminal.srt' },
  { mp4: 'sprint2-cena2-rabbitmq-ui.mp4', srt: 'sprint2-cena2-rabbitmq-ui.srt' },
  { mp4: 'sprint2-cena3-frontend.mp4', srt: 'sprint2-cena3-frontend.srt' },
  { cartao: [
    { t: 'Sprint 2 — o que foi entregue', tam: 64, negrito: true, cor: '0x60a5fa' },
    { t: '• Transferência assíncrona entre agências (exchange topic + 1 fila durável por agência)', tam: 36 },
    { t: '• Relógio vetorial no lugar do de Lamport: eventos causais × concorrentes', tam: 36 },
    { t: '• Consumidor idempotente, retenção na fila e estorno se o broker cair', tam: 36 },
    { t: '• Extra: dead-letter queue com inspeção e reprocessamento', tam: 36 },
    { t: 'github.com/GustavoFirmino/ICEIBank', tam: 32, cor: '0x94a3b8' },
  ], dur: 8 },
]);
console.log('video final do Sprint 2:', dur.toFixed(1) + ' s');
