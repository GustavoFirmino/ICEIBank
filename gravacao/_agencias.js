// Auxiliar de calibracao: sobe as 3 agencias (login de 2 h), cria a conta 0 e fica rodando ate ser encerrado (Ctrl+C).
const { Motor } = require('./motor');
(async () => {
  const m = new Motor();
  for (const i of [0, 1, 2]) m.iniciarAgencia(i, ['--jwt.expiration-seconds=7200']);
  await m.esperarAgencias();
  await new Promise((r) => setTimeout(r, 8000));
  await m.silencioso("$T = (Invoke-RestMethod -Uri http://localhost:4047/auth/login -Method Post -ContentType 'application/json' -Body '{\"username\":\"gustavo\",\"password\":\"senha123\"}').token; $h = @{Authorization=\"Bearer $T\"}; Invoke-RestMethod -Uri http://localhost:4047/contas -Method Post -Headers $h -ContentType 'application/json' -Body '{\"id\":0,\"titular\":\"Gustavo\",\"saldoInicial\":100}'");
  console.log('AGENCIAS PRONTAS (conta 0 criada)');
  process.on('SIGINT', async () => { await m.fechar(); process.exit(0); });
  setInterval(() => {}, 1 << 30);
})().catch((e) => { console.error('FALHOU', e); process.exit(1); });
