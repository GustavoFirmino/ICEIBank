// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - videos de apresentacao (Sprints 2 e 3)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Motor de gravacao: abre uma "tela de gravacao" em HTML (terminal + paineis de log ao vivo) no Chrome,
// grava com o screencast do Chrome (rapido e nitido, sem depender da captura do desktop do Windows),
// executa COMANDOS REAIS em uma sessao PowerShell persistente, acompanha os LOGS REAIS das agencias e o
// painel REAL do RabbitMQ, e registra as legendas (com o instante exato) para queimar no video depois.
'use strict';
const puppeteer = require('puppeteer-core');
const { spawn, execFileSync } = require('child_process');
const readline = require('readline');
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');

const RAIZ = path.resolve(__dirname, '..');
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const JAVA = 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.21.9-hotspot\\bin\\java.exe';
const dorme = (ms) => new Promise((r) => setTimeout(r, ms));

function acharFfmpegBin() {
  const base = path.join(process.env.LOCALAPPDATA, 'Microsoft', 'WinGet', 'Packages');
  for (const d of fs.readdirSync(base)) {
    if (!d.startsWith('Gyan.FFmpeg')) continue;
    for (const s of fs.readdirSync(path.join(base, d))) {
      const bin = path.join(base, d, s, 'bin');
      if (fs.existsSync(path.join(bin, 'ffmpeg.exe'))) return bin;
    }
  }
  throw new Error('ffmpeg nao encontrado');
}
const FFBIN = acharFfmpegBin();
process.env.PATH = FFBIN + ';' + process.env.PATH; // o screencast do puppeteer chama "ffmpeg" pelo PATH

function classeTipo(tipo) {
  if (/^(TORNOU_LIDER|LIDER_ANUNCIADO)$/.test(tipo)) return 't-lider';
  if (/^(LIDER_NAO_RESPONDEU|CREDITO_REMOTO_FALHOU|TRANSFERENCIA_REVERTIDA)$/.test(tipo)) return 't-falha';
  if (/^ELEICAO/.test(tipo)) return 't-eleicao';
  if (/^(TRANSFERENCIA|CREDITO_REMOTO)/.test(tipo)) return 't-msg';
  if (/^(CRIACAO_CONTA|DEPOSITO|SAQUE|SINCRONIZACAO_OFFLINE|LOGIN)$/.test(tipo)) return 't-conta';
  return 't-outro';
}

class Motor {
  constructor() {
    this.eventos = [];
    this.t0 = null;
    this.ps = null;
    this.agencias = {};
    this.tails = [];
    this.fila = Promise.resolve(); // serializa as escritas na pagina
    this.intervalos = [];
  }

  // opcoes.modoLogs: so paineis (largura = 1920 - largura do video do emulador)
  async abrir(opcoes = {}) {
    const largura = opcoes.modoLogs ? (opcoes.largura || 1434) : 1920;
    this.browser = await puppeteer.launch({
      executablePath: CHROME, headless: 'new',
      defaultViewport: { width: largura, height: 1080, deviceScaleFactor: 1 },
      args: ['--force-device-scale-factor=1', '--hide-scrollbars'],
    });
    this.page = await this.browser.newPage();
    const url = pathToFileURL(path.join(__dirname, 'terminal.html')).href + (opcoes.modoLogs ? `?modo=logs&largura=${largura}` : '');
    await this.page.goto(url);
  }

  // painel "quem e o lider": consulta GET /bully/lider em cada agencia a cada segundo
  iniciarLideres(paneId) {
    const id = setInterval(async () => {
      const ls = await Promise.all([0, 1, 2].map((i) => this.lider(i)));
      let h = '<table class="mq"><tr><th>agência</th><th>líder que ela conhece</th><th>estado</th></tr>';
      ls.forEach((l, i) => {
        const fora = l === 'x'; const sem = l === null;
        h += `<tr><td>Agência ${i}</td><td class="${fora ? 'bad' : sem ? '' : 'hi'}">${fora ? '—' : sem ? 'ainda nenhuma' : 'Agência ' + l}</td><td class="${fora ? 'bad' : ''}">${fora ? 'FORA DO AR' : 'no ar'}</td></tr>`;
      });
      this._pagina((p, x) => P.html(p, x), paneId, h + '</table>');
    }, 1000);
    this.intervalos.push(id);
  }

  // ---- gravacao ------------------------------------------------------------------------------------
  // Captura propria (screenshots PNG em loop, ~15/s): cada quadro guarda o instante REAL em que foi tirado.
  // Quadros identicos ao anterior nao sao gravados (so prolongam a duracao do anterior). O video final e
  // montado no parar() com o ffmpeg, respeitando esses tempos - assim o ritmo do video e o ritmo real.
  async gravar(mp4) {
    this.mp4 = mp4;
    this.dirQuadros = path.join(RAIZ, 'videos', '_quadros', path.basename(mp4, '.mp4'));
    fs.rmSync(this.dirQuadros, { recursive: true, force: true });
    fs.mkdirSync(this.dirQuadros, { recursive: true });
    this.quadros = [];            // { arq, t }
    this.gravando = true;
    this.eventos = [];
    this.t0 = Date.now();
    this.laco = (async () => {
      let ultimo = null; let i = 0;
      while (this.gravando) {
        const t = Date.now();
        let buf;
        try { buf = Buffer.from(await this.page.screenshot({ type: 'png' })); } catch (e) { await dorme(100); continue; }
        if (!ultimo || !buf.equals(ultimo)) {
          const arq = path.join(this.dirQuadros, `q${String(i++).padStart(6, '0')}.png`);
          fs.writeFileSync(arq, buf);
          this.quadros.push({ arq, t });
          ultimo = buf;
        }
        const dt = Date.now() - t;
        if (dt < 60) await dorme(60 - dt);
      }
    })();
  }
  async parar() {
    if (!this.gravando) return;
    await dorme(400);
    this.gravando = false; await this.laco;
    const fim = Date.now();
    const q = this.quadros;
    let lista = '';
    q.forEach((f, i) => {
      const prox = i + 1 < q.length ? q[i + 1].t : fim;
      const dur = Math.max(0.04, (prox - f.t) / 1000);
      lista += `file '${f.arq.replace(/\\/g, '/')}'\nduration ${dur.toFixed(3)}\n`;
    });
    lista += `file '${q[q.length - 1].arq.replace(/\\/g, '/')}'\n`;
    const arqLista = path.join(this.dirQuadros, 'lista.txt');
    fs.writeFileSync(arqLista, lista);
    if (fs.existsSync(this.mp4)) fs.unlinkSync(this.mp4);
    execFileSync(path.join(FFBIN, 'ffmpeg.exe'), ['-hide_banner', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', arqLista,
      '-vf', 'fps=15,format=yuv420p', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '20', '-movflags', '+faststart', '-y', this.mp4], { stdio: 'inherit' });
    fs.rmSync(this.dirQuadros, { recursive: true, force: true });
    this.t0 = null;
  }

  // legenda a partir de AGORA, por 'dur' segundos
  leg(texto, dur = 6) { if (this.t0) this.eventos.push({ t: (Date.now() - this.t0) / 1000, dur, texto }); }

  salvarSrt(arquivo, deslocamento = 0) {
    const f = (s) => {
      s = Math.max(0, s);
      const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), seg = Math.floor(s % 60), ms = Math.round((s - Math.floor(s)) * 1000);
      return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(seg).padStart(2, '0')},${String(ms).padStart(3, '0')}`;
    };
    // evita sobreposicao: cada legenda termina quando a proxima comeca
    const ev = this.eventos.map((e) => ({ ...e, ini: e.t - deslocamento }));
    let out = '';
    ev.forEach((e, i) => {
      let fim = e.ini + e.dur;
      if (i + 1 < ev.length) fim = Math.min(fim, ev[i + 1].ini - 0.05);
      out += `${i + 1}\n${f(e.ini)} --> ${f(Math.max(fim, e.ini + 0.8))}\n${e.texto}\n\n`;
    });
    fs.writeFileSync(arquivo, out, 'utf8');
  }

  // ---- pagina ------------------------------------------------------------------------------------------
  _pagina(fn, ...args) {
    this.fila = this.fila.then(() => this.page.evaluate(fn, ...args)).catch(() => {});
    return this.fila;
  }
  titulo(t) { return this._pagina((x) => T.titulo(x), t); }
  limpar() { return this._pagina(() => T.limpar()); }
  paineis(lista) { return this._pagina((l) => P.config(l), lista); }
  out(txt, cls) { return this._pagina((t, c) => T.out(t, c), txt, cls || ''); }
  nota(txt) { return this._pagina((t) => T.nota(t), txt); }
  async esperar(ms) { await dorme(ms); }

  // ---- shell real (PowerShell persistente) -------------------------------------------------------------
  _abrirPs() {
    // chcp 65001 antes do PowerShell: o stdin passa a ser lido como UTF-8 (acentos dos comandos saem certos)
    this.ps = spawn('cmd.exe', ['/d', '/c', 'chcp 65001>nul & powershell.exe -NoProfile -NoLogo -ExecutionPolicy Bypass -Command -'],
      { cwd: RAIZ, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
    this.psResolve = null;
    this.psBuf = [];
    readline.createInterface({ input: this.ps.stdout }).on('line', (l) => this._linhaPs(l, false));
    readline.createInterface({ input: this.ps.stderr }).on('line', (l) => this._linhaPs(l, true));
    this.ps.stdin.write("[Console]::OutputEncoding=[Text.Encoding]::UTF8; $OutputEncoding=[Text.Encoding]::UTF8; $ProgressPreference='SilentlyContinue'; $ErrorActionPreference='Continue'\r\n");
  }
  _linhaPs(l, erro) {
    const l2 = l.replace(/\u001b\[[0-9;]*m/g, '');
    if (l2.includes('@@FIM@@')) { const r = this.psResolve; this.psResolve = null; if (r) r(this.psBuf.join('\n')); this.psBuf = []; return; }
    if (this.psIgnorar) return;
    this.psBuf.push(l2);
    if (l2.trim() !== '' || !this.psPrimeiraVazia) this.out(l2, erro ? 'v' : '');
  }

  // Digita o comando na tela e o executa DE VERDADE; devolve a saida (texto).
  async cmd(mostrar, real) {
    if (!this.ps) this._abrirPs();
    await this._pagina((t) => T.cmd(t), mostrar);
    await dorme(300);
    const p = new Promise((r) => { this.psResolve = r; });
    this.ps.stdin.write((real || mostrar) + "\r\nWrite-Output '@@FIM@@'\r\n");
    return p;
  }
  // executa sem mostrar nada (preparacao)
  async silencioso(real) {
    if (!this.ps) this._abrirPs();
    this.psIgnorar = true;
    const p = new Promise((r) => { this.psResolve = r; });
    this.ps.stdin.write(real + "\r\nWrite-Output '@@FIM@@'\r\n");
    const s = await p; this.psIgnorar = false; return s;
  }

  // ---- logs das agencias -----------------------------------------------------------------------------
  // acompanha um arquivo de log e mostra no painel os eventos de negocio (EventLogService)
  tail(arquivo, paneId, filtro) {
    let pos = fs.existsSync(arquivo) ? 0 : 0;
    let resto = '';
    const re = /(\d{2}:\d{2}:\d{2}\.\d{3})[-+]\d{2}:\d{2}.*EventLogService\s+:\s+\[Vetor (\[[^\]]*\])\]\s+([A-Z_]+)\s+(\{.*\})/;
    const id = setInterval(() => {
      if (!fs.existsSync(arquivo)) return;
      const st = fs.statSync(arquivo);
      if (st.size <= pos) return;
      const fd = fs.openSync(arquivo, 'r');
      const buf = Buffer.alloc(st.size - pos);
      fs.readSync(fd, buf, 0, buf.length, pos); fs.closeSync(fd); pos = st.size;
      const linhas = (resto + buf.toString('utf8')).split(/\r?\n/); resto = linhas.pop();
      for (const l of linhas) {
        const m = re.exec(l); if (!m) continue;
        const tipo = m[3];
        if (filtro && !filtro.test(tipo)) continue;
        const detalhes = m[4].length > 70 ? m[4].slice(0, 67) + '...}' : m[4];
        this._pagina((a, b, c, d, e, f) => P.add(a, b, c, d, e), paneId, m[1], tipo, `${m[2]} ${detalhes}`, classeTipo(tipo));
      }
    }, 250);
    this.intervalos.push(id);
    return () => clearInterval(id);
  }

  // painel do RabbitMQ (API de gerenciamento): filas, prontas, consumidores, publicadas, entregues
  iniciarMq(paneId) {
    const auth = 'Basic ' + Buffer.from('guest:guest').toString('base64');
    const id = setInterval(async () => {
      try {
        const r = await fetch('http://localhost:15672/api/queues', { headers: { Authorization: auth }, signal: AbortSignal.timeout(2000) });
        const qs = (await r.json()).filter((q) => q.name.startsWith('fila-')).sort((a, b) => a.name.localeCompare(b.name));
        let h = '<table class="mq"><tr><th>fila</th><th>prontas</th><th>consum.</th><th>publicadas</th><th>entregues</th></tr>';
        for (const q of qs) {
          const ms = q.message_stats || {};
          const pr = q.messages_ready || 0;
          const cons = q.consumers || 0;
          h += `<tr><td>${q.name}</td><td class="${pr > 0 ? 'hi' : ''}">${pr}</td><td class="${cons === 0 && q.name !== 'fila-creditos-mortos' ? 'bad' : ''}">${cons}</td><td>${ms.publish || 0}</td><td>${ms.deliver_get || 0}</td></tr>`;
        }
        h += '</table>';
        await this._pagina((p, x) => P.html(p, x), paneId, h);
      } catch (e) { /* broker fora do ar */ }
    }, 1000);
    this.intervalos.push(id);
  }

  // ---- agencias ------------------------------------------------------------------------------------------
  iniciarAgencia(id, extra = []) {
    const dir = path.join(RAIZ, 'videos', '_logs'); fs.mkdirSync(dir, { recursive: true });
    const log = path.join(dir, `ag${id}-${Date.now()}.log`);
    const fd = fs.openSync(log, 'a');
    const p = spawn(JAVA, ['-jar', 'target\\agencia-1.0.0.jar', `--spring.profiles.active=agencia${id}`, ...extra],
      { cwd: path.join(RAIZ, 'agencia'), stdio: ['ignore', fd, fd], windowsHide: true });
    this.agencias[id] = { p, log };
    return log;
  }
  pararAgencia(id) {
    const a = this.agencias[id]; if (!a) return;
    try { execFileSync('taskkill', ['/PID', String(a.p.pid), '/T', '/F'], { stdio: 'ignore' }); } catch (e) { /* ja parou */ }
    delete this.agencias[id];
  }
  async lider(id) {
    try { const r = await fetch(`http://localhost:${4047 + id}/bully/lider`, { signal: AbortSignal.timeout(1500) }); return (await r.json()).liderAtual; } catch (e) { return 'x'; }
  }
  async esperarAgencias(ids = [0, 1, 2], max = 180000) {
    const ini = Date.now();
    while (Date.now() - ini < max) {
      const l = await Promise.all(ids.map((i) => this.lider(i)));
      if (l.every((x) => x !== 'x' && x !== null)) return;
      await dorme(1500);
    }
    throw new Error('agencias nao subiram');
  }

  async fechar() {
    this.intervalos.forEach(clearInterval);
    for (const id of Object.keys(this.agencias)) this.pararAgencia(id);
    if (this.ps) { try { this.ps.kill(); } catch (e) { /* */ } }
    if (this.browser) await this.browser.close();
  }
}

module.exports = { Motor, RAIZ, FFBIN, dorme };
