const puppeteer = require('puppeteer-core');
(async () => {
  const b = await puppeteer.launch({ executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', headless: 'new', defaultViewport: { width: 1920, height: 1080 } });
  const p = await b.newPage();
  await p.goto('http://localhost:15672/', { waitUntil: 'networkidle2' });
  console.log('inputs:', await p.$$eval('input', (els) => els.map((e) => e.name + ':' + e.type + ':' + e.id)));
  await p.type('input[name=username]', 'guest'); await p.type('input[name=password]', 'guest');
  await p.keyboard.press('Enter');
  await new Promise((r) => setTimeout(r, 4000));
  console.log('titulo:', await p.title());
  console.log('links nav:', await p.$$eval('a', (els) => els.map((e) => (e.getAttribute('href') || '') + '|' + e.textContent.trim()).filter((s) => /exchange|queue|overview/i.test(s)).slice(0, 12)));
  console.log('ids:', await p.$$eval('[id]', (els) => els.map((e) => e.id).filter((s) => /tab|menu|nav/i.test(s)).slice(0, 20)));
  await b.close();
})().catch((e) => { console.error('ERRO', e.message); process.exit(1); });
