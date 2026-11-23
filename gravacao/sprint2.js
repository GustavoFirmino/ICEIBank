// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - video do Sprint 2 (mensageria RabbitMQ + relogio vetorial)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Grava as cenas do Sprint 2 EXECUTANDO tudo de verdade: 3 agencias, RabbitMQ real, comandos no PowerShell,
// logs reais das agencias e painel real do RabbitMQ. Pre-requisito: RabbitMQ no ar (iniciar-rabbitmq*.ps1).
// Gera, em videos/: sprint2-cena1-terminal.mp4/.srt, sprint2-cena2-rabbitmq-ui.mp4/.srt, sprint2-cena3-frontend.mp4/.srt
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { Motor, RAIZ, dorme } = require('./motor');

const V = (n) => path.join(RAIZ, 'videos', n);
const FILTRO_S2 = /^(CRIACAO_CONTA|DEPOSITO|SAQUE|TRANSFERENCIA_|CREDITO_REMOTO_|SINCRONIZACAO_OFFLINE)/;

async function esperarUrl(url, max = 120000, auth) {
  const ini = Date.now();
  while (Date.now() - ini < max) {
    try { const r = await fetch(url, { headers: auth ? { Authorization: auth } : {}, signal: AbortSignal.timeout(2000) }); if (r.ok) return; } catch (e) { /* ainda nao */ }
    await dorme(1500);
  }
  throw new Error('nao respondeu: ' + url);
}

(async () => {
  const MQ = 'Basic ' + Buffer.from('guest:guest').toString('base64');
  await esperarUrl('http://localhost:15672/api/overview', 15000, MQ).catch(() => { throw new Error('RabbitMQ fora do ar: rode iniciar-rabbitmq-sem-docker.ps1 antes'); });

  // ---- preparacao (fora do video): logs limpos, 3 agencias no ar, frontend no ar -----------------------
  const dados = path.join(RAIZ, 'agencia', 'data');
  const bk = path.join(dados, 'backup-video-' + Date.now());
  fs.mkdirSync(bk, { recursive: true });
  for (const f of fs.readdirSync(dados)) if (f.endsWith('.jsonl')) fs.renameSync(path.join(dados, f), path.join(bk, f));

  const cenas = ((process.argv.find((a) => a.startsWith('--cenas=')) || '--cenas=1,2,3').split('=')[1]).split(',');
  // contadores do RabbitMQ zerados: apaga as filas (as agencias as recriam ao subir) para o painel mostrar so esta gravacao
  for (const q of ['fila-agencia-0', 'fila-agencia-1', 'fila-agencia-2', 'fila-creditos-mortos']) {
    try { await fetch('http://localhost:15672/api/queues/%2F/' + q, { method: 'DELETE', headers: { Authorization: MQ } }); } catch (e) { /* */ }
  }
  const m = new Motor();
  const logs = {};
  for (const i of [0, 1, 2]) logs[i] = m.iniciarAgencia(i);
  await m.esperarAgencias();
  const vite = spawn('npm.cmd', ['run', 'dev'], { cwd: path.join(RAIZ, 'frontend'), stdio: 'ignore', windowsHide: true, shell: true });
  const limpar = async () => {
    try { require('child_process').execFileSync('taskkill', ['/PID', String(vite.pid), '/T', '/F'], { stdio: 'ignore' }); } catch (e) { /* */ }
    await m.fechar();
  };
  try {
  await esperarUrl('http://localhost:5173/', 90000);
  await m.abrir();
  const salvaPagina = m.page;

  // contas ja existentes (quando a cena 1, que as cria ao vivo, nao e regravada)
  if (!cenas.includes('1')) {
    await m.silencioso("$T = (Invoke-RestMethod -Uri http://localhost:4047/auth/login -Method Post -ContentType 'application/json' -Body '{\"username\":\"gustavo\",\"password\":\"senha123\"}').token; $h = @{Authorization=\"Bearer $T\"}; Invoke-RestMethod -Uri http://localhost:4047/contas -Method Post -Headers $h -ContentType 'application/json' -Body '{\"id\":0,\"titular\":\"Gustavo\",\"saldoInicial\":100}'; Invoke-RestMethod -Uri http://localhost:4048/contas -Method Post -Headers $h -ContentType 'application/json' -Body '{\"id\":1,\"titular\":\"Aluno\",\"saldoInicial\":50}'");
  }

  // =========================== CENA 1: terminal + logs + painel do RabbitMQ ===============================
  if (cenas.includes('1')) {
  await m.paineis([
    { id: 'ag0', titulo: 'Agência 0', sub: ':4047 · eventos do log' },
    { id: 'ag1', titulo: 'Agência 1', sub: ':4048 · eventos do log' },
    { id: 'ag2', titulo: 'Agência 2', sub: ':4049 · eventos do log' },
    { id: 'mq', titulo: 'RabbitMQ — filas (ao vivo)', sub: 'API de gerenciamento' },
  ]);
  await m.titulo('Sprint 2 — Mensageria (RabbitMQ) e relógio vetorial');
  let paraTail1 = m.tail(logs[1], 'ag1', FILTRO_S2);
  m.tail(logs[0], 'ag0', FILTRO_S2); m.tail(logs[2], 'ag2', FILTRO_S2);
  m.iniciarMq('mq');
  await m.gravar(V('sprint2-cena1-terminal.mp4'));

  m.leg('Sprint 2: a transferência entre agências deixa de ser uma chamada REST direta e vira uma mensagem assíncrona no RabbitMQ.', 8);
  await m.esperar(3500);
  await m.cmd('Get-NetTCPConnection -LocalPort 4047,4048,4049 -State Listen | Sort-Object LocalPort | Select-Object LocalPort, OwningProcess | Format-Table -AutoSize');
  m.leg('As 3 agências (Spring Boot, portas 4047 a 4049) estão no ar. À direita: o log de cada uma e o painel ao vivo do RabbitMQ.', 8);
  await m.esperar(3500);

  // -- topologia do RabbitMQ
  m.leg('Topologia: uma exchange "iceibank.eventos" (tipo topic, durável) e uma fila durável por agência. A fila "creditos-mortos" é a dead-letter.', 9);
  await m.cmd("Invoke-RestMethod http://localhost:15672/api/exchanges/%2F/iceibank.eventos -Headers @{Authorization='Basic Z3Vlc3Q6Z3Vlc3Q='} | Select-Object name, type, durable | Format-List");
  await m.esperar(1500);
  await m.cmd("Invoke-RestMethod http://localhost:15672/api/queues -Headers @{Authorization='Basic Z3Vlc3Q6Z3Vlc3Q='} | Where-Object name -like 'fila-*' | Sort-Object name | Select-Object name, durable, consumers, messages_ready | Format-Table -AutoSize");
  await m.esperar(3500);

  // -- JWT + contas
  m.leg('Continua do Sprint 1: autenticação por JWT. Fazemos login e usamos o token nas chamadas seguintes.', 7);
  await m.cmd("$T = (Invoke-RestMethod -Uri http://localhost:4047/auth/login -Method Post -ContentType 'application/json' -Body '{\"username\":\"gustavo\",\"password\":\"senha123\"}').token; $h = @{Authorization=\"Bearer $T\"}; 'token JWT obtido: ' + $T.Substring(0,24) + '...'");
  await m.esperar(2500);
  m.leg('Cada conta pertence a uma só agência (id % 3). A conta 0 fica na Agência 0 e a conta 1 na Agência 1.', 8);
  await m.cmd("Invoke-RestMethod -Uri http://localhost:4047/contas -Method Post -Headers $h -ContentType 'application/json' -Body '{\"id\":0,\"titular\":\"Gustavo\",\"saldoInicial\":100}' | ConvertTo-Json -Compress");
  await m.esperar(1500);
  await m.cmd("Invoke-RestMethod -Uri http://localhost:4048/contas -Method Post -Headers $h -ContentType 'application/json' -Body '{\"id\":1,\"titular\":\"Aluno\",\"saldoInicial\":50}' | ConvertTo-Json -Compress");
  await m.esperar(3000);

  // -- transferencia assincrona + vetorial
  m.leg('Transferência de R$ 30 da conta 0 (Agência 0) para a conta 1 (Agência 1): a Agência 0 debita e PUBLICA a mensagem no RabbitMQ.', 9);
  await m.cmd("Invoke-RestMethod -Uri http://localhost:4047/transferencias -Method Post -Headers $h -ContentType 'application/json' -Body '{\"idOrigem\":0,\"idDestino\":1,\"valor\":30,\"idOperacao\":\"video-1\"}' | ConvertTo-Json -Compress");
  await m.esperar(1200);
  m.leg('Relógio vetorial: ao ENVIAR, a Agência 0 incrementa a sua posição e anexa o vetor inteiro; ao RECEBER, a Agência 1 faz o máximo posição a posição e incrementa a sua.', 11);
  await m.esperar(5500);
  await m.cmd("'saldo conta 0 (Ag.0): ' + (Invoke-RestMethod http://localhost:4047/contas/0 -Headers $h).saldo; 'saldo conta 1 (Ag.1): ' + (Invoke-RestMethod http://localhost:4048/contas/1 -Headers $h).saldo");
  m.leg('O débito foi na origem e o crédito chegou ao destino de forma assíncrona (veja TRANSFERENCIA_PUBLICADA → TRANSFERENCIA_CREDITO_REMOTO nos logs).', 9);
  await m.esperar(4500);

  // -- idempotencia
  m.leg('Idempotência: reenviar a MESMA operação (mesmo idOperacao) não debita nem credita de novo.', 8);
  await m.cmd("Invoke-RestMethod -Uri http://localhost:4047/transferencias -Method Post -Headers $h -ContentType 'application/json' -Body '{\"idOrigem\":0,\"idDestino\":1,\"valor\":30,\"idOperacao\":\"video-1\"}' | ConvertTo-Json -Compress");
  await m.esperar(1500);
  await m.cmd("'saldo conta 0: ' + (Invoke-RestMethod http://localhost:4047/contas/0 -Headers $h).saldo + ' | saldo conta 1: ' + (Invoke-RestMethod http://localhost:4048/contas/1 -Headers $h).saldo");
  await m.esperar(3500);

  // -- resiliencia: agencia de destino fora do ar
  m.leg('Resiliência: derrubamos a Agência 1 (destino). A transferência continua funcionando — a mensagem fica retida na fila.', 9);
  await m.cmd("Stop-Process -Id (Get-NetTCPConnection -LocalPort 4048 -State Listen).OwningProcess -Force; 'Agência 1 derrubada'");
  await m.esperar(3500);
  await m.cmd("Invoke-RestMethod -Uri http://localhost:4047/transferencias -Method Post -Headers $h -ContentType 'application/json' -Body '{\"idOrigem\":0,\"idDestino\":1,\"valor\":5,\"idOperacao\":\"video-2\"}' | ConvertTo-Json -Compress");
  m.leg('Resposta 200 mesmo com o destino fora: no painel, fila-agencia-1 está com 1 mensagem pronta e 0 consumidores.', 9);
  await m.esperar(6000);

  // -- agencia volta: conta em memoria sumiu -> dead-letter
  m.leg('A Agência 1 volta. As contas ficam só em memória, então a conta 1 não existe mais: o crédito não pode ser aplicado e vai para a dead-letter.', 11);
  paraTail1();
  logs[1] = m.iniciarAgencia(1);
  paraTail1 = m.tail(logs[1], 'ag1', FILTRO_S2);
  await m.cmd("Write-Output '# aguardando a Agência 1 voltar e consumir a mensagem retida...'; do { Start-Sleep -Seconds 2; try { $ok = (Invoke-RestMethod http://localhost:4048/bully/lider -TimeoutSec 2) } catch { $ok = $null } } while (-not $ok); Start-Sleep -Seconds 4; 'Agência 1 no ar'");
  await m.esperar(1500);
  await m.cmd("$T = (Invoke-RestMethod -Uri http://localhost:4048/auth/login -Method Post -ContentType 'application/json' -Body '{\"username\":\"gustavo\",\"password\":\"senha123\"}').token; $h = @{Authorization=\"Bearer $T\"}; Invoke-RestMethod http://localhost:4048/mensagens-mortas -Headers $h | ConvertTo-Json -Compress");
  m.leg('Funcionalidade adicional: GET /mensagens-mortas lista os créditos retidos na dead-letter queue, sem perder dinheiro.', 9);
  await m.esperar(5000);

  // -- reprocessar
  m.leg('Recriamos a conta 1 e reprocessamos: a mensagem é republicada e o crédito finalmente é aplicado.', 9);
  await m.cmd("Invoke-RestMethod -Uri http://localhost:4048/contas -Method Post -Headers $h -ContentType 'application/json' -Body '{\"id\":1,\"titular\":\"Aluno\",\"saldoInicial\":50}' | ConvertTo-Json -Compress");
  await m.esperar(1500);
  await m.cmd("Invoke-RestMethod -Uri http://localhost:4048/mensagens-mortas/reprocessar -Method Post -Headers $h | ConvertTo-Json -Compress");
  await m.esperar(3500);
  await m.cmd("'saldo conta 1 apos reprocessar: ' + (Invoke-RestMethod http://localhost:4048/contas/1 -Headers $h).saldo + '  (50 inicial + 5 da transferencia reprocessada)'");
  await m.esperar(4000);

  // -- linha do tempo causal (MesclarLogs)
  m.leg('Linha do tempo unificada: o MesclarLogs compara os vetores e prova o que é causal (ANTES) e o que é concorrente entre agências.', 10);
  await m.limpar();
  await m.cmd("Push-Location agencia; java -cp target\\agencia-1.0.0.jar '-Dloader.main=br.pucminas.labdamd.iceibank.agencia.ferramentas.MesclarLogs' org.springframework.boot.loader.launch.PropertiesLauncher 2>&1 | Select-Object -Last 22; Pop-Location");
  await m.esperar(9000);
  m.leg('Fim da cena: mensageria assíncrona, relógio vetorial, idempotência, retenção na fila e dead-letter com reprocessamento.', 8);
  await m.esperar(4500);
  await m.parar();
  m.salvarSrt(V('sprint2-cena1-terminal.srt'));
  }

  // =========================== CENA 2: painel do RabbitMQ (interface real) ===============================
  if (cenas.includes('2')) {
  const p2 = await m.browser.newPage();
  await p2.setViewport({ width: 1920, height: 1080, deviceScaleFactor: 1 });
  await p2.goto('http://localhost:15672/');
  m.page = p2;
  await m.gravar(V('sprint2-cena2-rabbitmq-ui.mp4'));
  m.leg('A interface do RabbitMQ Manager (a mesma que as agências usam): entramos com a conta de teste local.', 6);
  await m.esperar(2000);
  await p2.type('input[name=username]', 'guest', { delay: 60 });
  await p2.type('input[name=password]', 'guest', { delay: 60 });
  await p2.keyboard.press('Enter');
  await p2.waitForSelector('a[href="#/exchanges"]', { timeout: 20000 });
  await p2.evaluate(() => { document.body.style.zoom = '1.45'; });   // interface maior, legivel no video
  await m.esperar(2500);
  m.leg('Aba Exchanges: a exchange "iceibank.eventos" (tipo topic, durável) é o ponto de publicação das agências.', 8);
  await p2.evaluate(() => { location.hash = '#/exchanges'; });
  await m.esperar(6000);
  m.leg('Dentro dela, os bindings: a chave agencia.<destino>.creditar leva a mensagem para a fila da agência de destino.', 10);
  await p2.evaluate(() => { location.hash = '#/exchanges/%2F/iceibank.eventos'; });
  await m.esperar(4500);
  // expande a secao "Bindings" (as chaves de roteamento para as filas)
  await p2.evaluate(() => { [...document.querySelectorAll('h2')].filter((h) => /Bindings/.test(h.textContent)).forEach((h) => h.click()); });
  await m.esperar(1500);
  await p2.evaluate(() => window.scrollTo({ top: 420, behavior: 'smooth' }));
  await m.esperar(7000);
  m.leg('Aba Queues: uma fila durável por agência (cada uma com 1 consumidor) e a fila de mensagens mortas (dead-letter).', 10);
  await p2.evaluate(() => { location.hash = '#/queues'; });
  await m.esperar(9000);
  await m.parar();
  m.salvarSrt(V('sprint2-cena2-rabbitmq-ui.srt'));
  m.page = salvaPagina;
  }

  // =========================== CENA 3: frontend web (regressao do Sprint 1) ===============================
  if (cenas.includes('3')) {
  const p3 = await m.browser.newPage();
  await p3.setViewport({ width: 1280, height: 1100, deviceScaleFactor: 1 });
  await p3.goto('http://localhost:5173/', { waitUntil: 'networkidle2' });
  await p3.evaluate(() => { document.body.style.zoom = '1.45'; });
  m.page = p3;
  const preencher = (sel, v) => p3.$eval(sel, (el, x) => {
    const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    set.call(el, x); el.dispatchEvent(new Event('input', { bubbles: true }));
  }, v);
  await m.gravar(V('sprint2-cena3-frontend.mp4'));
  m.leg('Continuidade: o frontend web do Sprint 1 continua funcionando. Login na Agência 0.', 6);
  await m.esperar(2500);
  await preencher('#username', 'gustavo'); await preencher('#password', 'senha123');
  await p3.click('button[type=submit]');
  await p3.waitForSelector('#idConta', { timeout: 20000 });
  await m.esperar(1000);
  m.leg('Consulta de saldo da conta 0.', 4);
  await preencher('#idConta', '0');
  await p3.click('form:has(#idConta) button[type=submit]');
  await p3.waitForSelector('.saldo', { timeout: 20000 });
  await m.esperar(2500);
  m.leg('Transferência entre agências (0 → 1) pela interface: agora aparece "publicada (entrega assíncrona)".', 9);
  await p3.$eval('#idOrigem', (e) => e.scrollIntoView({ block: 'center', behavior: 'smooth' }));
  await m.esperar(1500);
  await preencher('#idOrigem', '0'); await preencher('#idDestino', '1'); await preencher('#valorTransf', '5');
  await m.esperar(2000);
  await p3.$eval('form:has(#idOrigem)', (f) => f.requestSubmit());
  await p3.waitForFunction(() => document.body.innerText.includes('Transferencia publicada'), { timeout: 40000 });
  await m.esperar(6000);
  await m.parar();
  m.salvarSrt(V('sprint2-cena3-frontend.srt'));

  m.page = salvaPagina;
  }
  console.log('FIM sprint2');
  } finally {
    await limpar();
  }
})().catch((e) => { console.error('FALHOU', e); process.exit(1); });
