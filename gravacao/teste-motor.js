// Teste do motor de gravacao (sem agencias): terminal + paineis, comandos reais e legendas.
const path = require('path');
const { Motor, RAIZ } = require('./motor');
(async () => {
  const m = new Motor();
  await m.abrir();
  await m.paineis([{ id: 'ag0', titulo: 'Agência 0', sub: ':4047' }, { id: 'ag1', titulo: 'Agência 1', sub: ':4048' }, { id: 'mq', titulo: 'RabbitMQ — filas', sub: 'API de gerenciamento' }]);
  await m.titulo('Teste do motor — Sprint 2');
  await m.gravar(path.join(RAIZ, 'videos', 'teste-motor.mp4'));
  m.leg('Legenda de teste: o comando abaixo é executado de verdade.', 5);
  await m.cmd('Get-Date');
  await m.esperar(800);
  m.leg('Saída real do PowerShell, linha por linha.', 5);
  await m.cmd('1..4 | ForEach-Object { "linha real $_ - " + (Get-Date -Format HH:mm:ss) }');
  await m._pagina(() => { P.add('ag0', '10:00:00.123', 'TRANSFERENCIA_PUBLICADA', '[1, 0, 0] {valor=30}', 't-msg'); P.add('ag1', '10:00:00.456', 'TRANSFERENCIA_CREDITO_REMOTO', '[1, 1, 0] {valor=30}', 't-msg'); P.add('mq', '', '', '', ''); });
  await m.esperar(1500);
  await m.cmd('Write-Output "== erro de teste"; Write-Output "Conflito detectado"');
  await m.esperar(2000);
  await m.parar();
  m.salvarSrt(path.join(RAIZ, 'videos', 'teste-motor.srt'), 0);
  await m.fechar();
  console.log('FIM');
})().catch((e) => { console.error('FALHOU', e); process.exit(1); });
