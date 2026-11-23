const path = require('path');
const { Motor, RAIZ } = require('./motor');
(async () => {
  const m = new Motor();
  const t0 = Date.now();
  await m.abrir();
  console.log('abrir ms', Date.now() - t0);
  await m.paineis([{ id: 'ag0', titulo: 'A0' }]);
  const r = [];
  for (const [tipo, q] of [['jpeg', 70], ['jpeg', 85], ['png', 0]]) {
    const ts = [];
    for (let i = 0; i < 6; i++) {
      const t = Date.now();
      const b = await m.page.screenshot(tipo === 'jpeg' ? { type: 'jpeg', quality: q } : { type: 'png' });
      ts.push(Date.now() - t + 'ms/' + Math.round(b.length / 1024) + 'KB');
    }
    r.push(tipo + q + ': ' + ts.join(' '));
  }
  const t = Date.now();
  await m.cmd('Get-Date');
  r.push('cmd (digitar+executar) ms: ' + (Date.now() - t));
  const t2 = Date.now();
  await m.cmd('Write-Output ok');
  r.push('cmd2 ms: ' + (Date.now() - t2));
  require('fs').writeFileSync(path.join(RAIZ, 'videos', 'bench-shot.txt'), r.join('\n'));
  await m.fechar();
})().catch((e) => { console.error(e); process.exit(1); });
