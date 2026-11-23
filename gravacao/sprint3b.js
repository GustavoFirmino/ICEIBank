// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - video do Sprint 3, parte B (app Flutter no emulador Android + logs das agencias)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Grava o emulador com o screenrecord do proprio Android (video nativo) e, em paralelo, os LOGS REAIS das agencias
// (painel em HTML). Os dois sao juntados lado a lado pelo montar-sprint3.js. Executa tudo de verdade:
// login, painel da lider, failover com o app aberto, modo aviao, fila offline, sincronizacao e conflito (409).
// Pre-requisitos: emulador "iceibank_pixel" ligado (adb) e o APK mais recente instalado.
// Uso: node gravacao/sprint3b.js [--partes=1,2,3]
const fs = require('fs');
const path = require('path');
const { spawn, execFileSync } = require('child_process');
const { Motor, RAIZ, dorme } = require('./motor');
const V = (n) => path.join(RAIZ, 'videos', n);
const ADB = path.join(process.env.USERPROFILE, 'tools', 'android-sdk', 'platform-tools', 'adb.exe');
const FILTRO_S3 = /^(ELEICAO|TORNOU_LIDER|LIDER_|CRIACAO_CONTA|DEPOSITO|SAQUE|SINCRONIZACAO_OFFLINE)/;
const PKG = 'br.pucminas.labdamd.iceibank_mobile';

// ---- adb ------------------------------------------------------------------------------------------------
const adb = (...a) => execFileSync(ADB, a, { encoding: 'utf8', timeout: 60000 });
const tap = (xy) => adb('shell', 'input', 'tap', String(xy[0]), String(xy[1]));
const texto = (t) => adb('shell', 'input', 'text', t);
const tecla = (k) => adb('shell', 'input', 'keyevent', String(k));
const aviao = (on) => adb('shell', 'cmd', 'connectivity', 'airplane-mode', on ? 'enable' : 'disable');

// coordenadas (pixels reais da tela 1080x2400 do emulador) - calibradas com screenshots do app
const XY = require('./coordenadas.json');

// Grava a tela do emulador (screenrecord) -> devolve funcao que para e traz o arquivo
function gravarEmulador(nome, limite = 170) {
  const remoto = `/sdcard/${nome}.mp4`;
  const local = V(`${nome}.mp4`);
  const p = spawn(ADB, ['shell', 'screenrecord', '--bit-rate', '6000000', '--size', '540x1200', '--time-limit', String(limite), remoto], { stdio: 'ignore' });
  return async () => {
    try { adb('shell', 'pkill', '-INT', 'screenrecord'); } catch (e) { /* ja terminou */ }
    await dorme(2500);
    try { p.kill(); } catch (e) { /* */ }
    if (fs.existsSync(local)) fs.unlinkSync(local);
    adb('pull', remoto, local);
  };
}

async function parte(m, nome, legendaNome, fn) {
  const parar = gravarEmulador(nome);
  await dorme(1200);                       // o screenrecord leva ~0,5 s para o 1o quadro
  await m.gravar(V(`${legendaNome}-logs.mp4`));
  await fn();
  await m.parar();
  m.salvarSrt(V(`${legendaNome}.srt`));
  await parar();
}

(async () => {
  const partes = ((process.argv.find((a) => a.startsWith('--partes=')) || '--partes=1,2,3').split('=')[1]).split(',');
  const m = new Motor();
  try {
    // ---- preparacao (fora do video) --------------------------------------------------------------------
    const dados = path.join(RAIZ, 'agencia', 'data');
    const bk = path.join(dados, 'backup-video-' + Date.now());
    fs.mkdirSync(bk, { recursive: true });
    for (const f of fs.readdirSync(dados)) if (f.endsWith('.jsonl')) fs.renameSync(path.join(dados, f), path.join(bk, f));
    const logs = {};
    for (const i of [0, 1, 2]) logs[i] = m.iniciarAgencia(i, ['--jwt.expiration-seconds=7200']);
    await m.esperarAgencias();
    await dorme(8000);
    await m.silencioso("$T = (Invoke-RestMethod -Uri http://localhost:4047/auth/login -Method Post -ContentType 'application/json' -Body '{\"username\":\"gustavo\",\"password\":\"senha123\"}').token; $h = @{Authorization=\"Bearer $T\"}; Invoke-RestMethod -Uri http://localhost:4047/contas -Method Post -Headers $h -ContentType 'application/json' -Body '{\"id\":0,\"titular\":\"Gustavo\",\"saldoInicial\":100}'");
    adb('shell', 'pm', 'clear', PKG);
    aviao(false);
    adb('shell', 'monkey', '-p', PKG, '-c', 'android.intent.category.LAUNCHER', '1');
    await dorme(40000);                    // o APK de debug demora a abrir no primeiro arranque

    await m.abrir({ modoLogs: true, largura: 1434 });
    await m.paineis([
      { id: 'ag0', titulo: 'Agência 0', sub: ':4047 · eventos do log' },
      { id: 'ag1', titulo: 'Agência 1', sub: ':4048 · eventos do log' },
      { id: 'ag2', titulo: 'Agência 2', sub: ':4049 · eventos do log' },
      { id: 'lid', titulo: 'Quem é a líder? (consulta ao vivo)', sub: 'GET /bully/lider' },
    ]);
    await m.titulo('Sprint 3 — App Flutter no emulador Android + logs reais das agências');
    m.iniciarLideres('lid');
    m.tail(logs[0], 'ag0', FILTRO_S3); m.tail(logs[1], 'ag1', FILTRO_S3); m.tail(logs[2], 'ag2', FILTRO_S3);

    // =========================== PARTE 1: login, painel da lider e failover ===========================
    if (partes.includes('1')) await parte(m, 's3b1', 'sprint3-cena2a', async () => {
      m.leg('App Flutter (Parte D): o primeiro cliente mobile do projeto reaproveita a mesma API REST. O painel "Líder atual" já mostra a líder das agências.', 10);
      await m.esperar(5000);
      m.leg('Login com JWT na Agência de entrada 0.', 5);
      tap(XY.login.usuario); await dorme(700); texto('gustavo'); await dorme(500);
      tecla(61); await dorme(500); texto('senha123'); await dorme(500); tecla(66);
      await m.esperar(6000);
      m.leg('Abrimos a conta 0: saldo R$ 100, vindo da Agência 0 (conta 0 % 3 = agência 0).', 7);
      tap(XY.home.conta); await dorme(700); texto('0'); await dorme(500); tecla(66);
      await m.esperar(7000);
      m.leg('Failover com o app aberto: derrubamos a líder (Agência 2). Ninguém toca na tela do app.', 9);
      m.pararAgencia(2);
      await m.esperar(5000);
      m.leg('Em poucos segundos as agências reelegem a Agência 1 e o painel do app atualiza sozinho, registrando a troca "Ag. 2 passou para Ag. 1".', 12);
      await m.esperar(14000);
    });

    // =========================== PARTE 2: modo aviao, fila offline e sincronizacao ====================
    if (partes.includes('2')) await parte(m, 's3b2', 'sprint3-cena2b', async () => {
      m.leg('Offline-first (Parte F): colocamos o emulador em modo avião — o app perde a conexão com todas as agências.', 9);
      await m.esperar(2500);
      aviao(true);
      await m.esperar(6000);
      m.leg('Depositar R$ 40 offline: a operação não falha. Vai para a fila local e a tela já mostra o saldo PROVISÓRIO (R$ 140) com a marca "pendente".', 12);
      tap(XY.conta.chip40); await dorme(900); tap(XY.conta.depositar);
      await m.esperar(9000);
      m.leg('Reconectamos (modo avião desligado). O app detecta a rede e sincroniza sozinho, sem ninguém tocar em nada.', 10);
      aviao(false);
      await m.esperar(9000);
      m.leg('Aviso "1 operação sincronizada" e saldo confirmado pela agência: R$ 140. O vetor da agência não tinha mudado, então a operação foi aplicada.', 11);
      await m.esperar(8000);
    });

    // =========================== PARTE 3: conflito (409) ============================================
    if (partes.includes('3')) await parte(m, 's3b3', 'sprint3-cena2c', async () => {
      m.leg('Conflito: de novo em modo avião, fazemos um saque pendente de R$ 10...', 8);
      aviao(true);
      await m.esperar(5000);
      tap(XY.conta.chip10); await dorme(900); tap(XY.conta.sacar);
      await m.esperar(5000);
      m.leg('...e, enquanto o app está offline, a conta muda por outro caminho: um depósito de R$ 7 direto na agência.', 9);
      await m.silencioso("$T = (Invoke-RestMethod -Uri http://localhost:4047/auth/login -Method Post -ContentType 'application/json' -Body '{\"username\":\"gustavo\",\"password\":\"senha123\"}').token; $h = @{Authorization=\"Bearer $T\"}; Invoke-RestMethod -Uri http://localhost:4047/contas/0/depositar -Method Post -Headers $h -ContentType 'application/json' -Body '{\"valor\":7}'");
      await m.esperar(5000);
      m.leg('Ao reconectar, a agência vê que o vetor mudou e responde 409. O app NÃO descarta nem aplica em silêncio: mostra o saldo real e pergunta o que fazer.', 12);
      aviao(false);
      await m.esperar(13000);
      m.leg('"Aplicar mesmo assim": o app reenvia com o vetor atual devolvido no 409 e a operação é aceita.', 9);
      tap(XY.conta.aplicar);
      await m.esperar(9000);
    });

    console.log('FIM sprint3b');
  } finally {
    await m.fechar();
  }
})().catch((e) => { console.error('FALHOU', e); process.exit(1); });
