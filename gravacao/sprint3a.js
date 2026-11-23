// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - video do Sprint 3, parte A (Bully, failover, historico e sincronizacao no backend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Executa DE VERDADE: sobe as 3 agencias ao vivo (eleicao inicial), derruba a lider, mostra o failover, o historico de
// liderancas, a volta da lider e o endpoint de sincronizacao com 409. Gera videos/sprint3-cena1-bully.mp4/.srt
// Nao precisa de RabbitMQ (o Bully e a sincronizacao nao dependem do broker).
const fs = require('fs');
const path = require('path');
const { Motor, RAIZ } = require('./motor');
const V = (n) => path.join(RAIZ, 'videos', n);
const FILTRO_S3 = /^(ELEICAO|TORNOU_LIDER|LIDER_|CRIACAO_CONTA|DEPOSITO|SAQUE|SINCRONIZACAO_OFFLINE)/;

(async () => {
  // logs limpos para o historico mostrar so esta gravacao
  const dados = path.join(RAIZ, 'agencia', 'data');
  const bk = path.join(dados, 'backup-video-' + Date.now());
  fs.mkdirSync(bk, { recursive: true });
  for (const f of fs.readdirSync(dados)) if (f.endsWith('.jsonl')) fs.renameSync(path.join(dados, f), path.join(bk, f));

  const m = new Motor();
  try {
    await m.abrir();
    await m.paineis([
      { id: 'ag0', titulo: 'Agência 0', sub: ':4047 · eventos do log' },
      { id: 'ag1', titulo: 'Agência 1', sub: ':4048 · eventos do log' },
      { id: 'ag2', titulo: 'Agência 2', sub: ':4049 · eventos do log' },
      { id: 'lid', titulo: 'Quem é a líder? (consulta ao vivo)', sub: 'GET /bully/lider' },
    ]);
    await m.titulo('Sprint 3 — Consenso: eleição de líder (algoritmo de Bully)');
    m.iniciarLideres('lid');
    await m.gravar(V('sprint3-cena1-bully.mp4'));

    m.leg('Sprint 3: as 3 agências elegem uma líder entre si com o algoritmo de Bully — a agência ATIVA de maior ID sempre vence.', 8);
    await m.esperar(2500);
    await m.cmd("Write-Output '# subindo as 3 agências ao mesmo tempo (Spring Boot)...'");
    // sobem AO VIVO: o video mostra a eleicao inicial
    const logs = {};
    for (const i of [0, 1, 2]) logs[i] = m.iniciarAgencia(i);
    m.tail(logs[0], 'ag0', FILTRO_S3); m.tail(logs[1], 'ag1', FILTRO_S3);
    let paraTail2 = m.tail(logs[2], 'ag2', FILTRO_S3);
    m.leg('Ao subir, cada agência inicia uma eleição: manda ELEIÇÃO só para quem tem ID MAIOR. Se ninguém responde, ela vira líder e anuncia (COORDENADOR).', 12);
    await m.esperarAgencias();
    m.leg('É normal haver uma "tempestade" de eleições na subida (várias TORNOU_LIDER): o algoritmo sempre converge para o maior ID.', 10);
    await m.esperar(9000);
    await m.cmd("0..2 | ForEach-Object { 'Agência ' + $_ + ' -> ' + (Invoke-RestMethod ('http://localhost:' + (4047 + $_) + '/bully/lider') | ConvertTo-Json -Compress) }");
    m.leg('As 3 agências concordam: a líder é a Agência 2 (o maior ID).', 8);
    await m.esperar(5500);

    // ---- failover
    m.leg('Failover: derrubamos a líder (Agência 2). A cada 3 s cada agência faz um heartbeat na líder; sem resposta, dispara nova eleição sozinha.', 11);
    await m.cmd("Stop-Process -Id (Get-NetTCPConnection -LocalPort 4049 -State Listen).OwningProcess -Force; 'Agência 2 (líder) derrubada'");
    await m.esperar(11000);
    m.leg('LIDER_NAO_RESPONDEU → ELEICAO → TORNOU_LIDER: a Agência 1 (maior ID ativo) assume. Nenhuma intervenção manual.', 10);
    await m.cmd("0..1 | ForEach-Object { 'Agência ' + $_ + ' -> ' + (Invoke-RestMethod ('http://localhost:' + (4047 + $_) + '/bully/lider') | ConvertTo-Json -Compress) }");
    await m.esperar(6000);

    // ---- historico
    m.leg('Observabilidade: o historico-lideranca reconstrói a linha do tempo de líderes a partir dos logs das 3 agências.', 9);
    await m.limpar();
    await m.cmd("Push-Location agencia; java -cp target\\agencia-1.0.0.jar '-Dloader.main=br.pucminas.labdamd.iceibank.agencia.ferramentas.HistoricoLideranca' org.springframework.boot.loader.launch.PropertiesLauncher 2>&1 | Select-Object -Last 14; Pop-Location");
    await m.esperar(9000);

    // ---- volta da lider
    m.leg('A Agência 2 volta: inicia uma eleição ao subir e, por ter o maior ID, retoma a liderança.', 9);
    paraTail2();
    logs[2] = m.iniciarAgencia(2);
    paraTail2 = m.tail(logs[2], 'ag2', FILTRO_S3);
    await m.cmd("Write-Output '# Agência 2 subindo de novo...'; do { Start-Sleep -Seconds 2; try { $ok = Invoke-RestMethod http://localhost:4049/bully/lider -TimeoutSec 2 } catch { $ok = $null } } while (-not $ok); Start-Sleep -Seconds 5; 'Agência 2 no ar'");
    await m.esperar(1500);
    await m.cmd("0..2 | ForEach-Object { 'Agência ' + $_ + ' -> ' + (Invoke-RestMethod ('http://localhost:' + (4047 + $_) + '/bully/lider') | ConvertTo-Json -Compress) }");
    await m.esperar(6000);

    // ---- sincronizacao (backend offline-first)
    await m.limpar();
    m.leg('Parte E: sincronização offline-first. O app guarda o VETOR da agência na última leitura e o devolve junto com a operação pendente.', 10);
    await m.cmd("$T = (Invoke-RestMethod -Uri http://localhost:4047/auth/login -Method Post -ContentType 'application/json' -Body '{\"username\":\"gustavo\",\"password\":\"senha123\"}').token; $h = @{Authorization=\"Bearer $T\"}; Invoke-RestMethod -Uri http://localhost:4047/contas -Method Post -Headers $h -ContentType 'application/json' -Body '{\"id\":0,\"titular\":\"Gustavo\",\"saldoInicial\":100}' | ConvertTo-Json -Compress");
    await m.esperar(1500);
    await m.cmd("$c = Invoke-RestMethod http://localhost:4047/contas/0 -Headers $h; $v = $c.vetorAtual; $c | ConvertTo-Json -Compress");
    m.leg('GET /contas/0 agora devolve também o vetorAtual da agência — é o "vetor conhecido" que o app guarda.', 8);
    await m.esperar(4500);
    m.leg('Enquanto o app estava offline, a conta mudou por outro caminho (um depósito de R$ 10).', 7);
    await m.cmd("Invoke-RestMethod -Uri http://localhost:4047/contas/0/depositar -Method Post -Headers $h -ContentType 'application/json' -Body '{\"valor\":10}' | ConvertTo-Json -Compress");
    await m.esperar(3000);
    m.leg('O app sincroniza um saque pendente com o vetor ANTIGO: a agência detecta que mudou e responde 409, devolvendo o estado atual (não aplica às cegas).', 11);
    await m.cmd("try { Invoke-RestMethod -Uri http://localhost:4047/contas/0/sincronizar -Method Post -Headers $h -ContentType 'application/json' -Body (@{tipo='SAQUE';valor=30;vetorConhecido=$v} | ConvertTo-Json -Compress) } catch { $r = $_.Exception.Response; $corpo = (New-Object IO.StreamReader($r.GetResponseStream())).ReadToEnd(); 'HTTP ' + [int]$r.StatusCode + ' ' + $corpo; $v2 = @(($corpo | ConvertFrom-Json).vetorAtual) }");
    await m.esperar(7000);
    m.leg('Com o vetorAtual devolvido no 409 (o app/usuário decidiu aplicar), a mesma operação é aceita.', 9);
    await m.cmd("Invoke-RestMethod -Uri http://localhost:4047/contas/0/sincronizar -Method Post -Headers $h -ContentType 'application/json' -Body (@{tipo='SAQUE';valor=30;vetorConhecido=$v2} | ConvertTo-Json -Compress) | ConvertTo-Json -Compress");
    await m.esperar(6000);
    m.leg('Resumo da parte A: Bully (eleição e coordenador), heartbeat com failover automático, histórico de lideranças e sincronização com detecção de conflito por vetor.', 9);
    await m.esperar(6000);
    await m.parar();
    m.salvarSrt(V('sprint3-cena1-bully.srt'));
    console.log('FIM sprint3a');
  } finally {
    await m.fechar();
  }
})().catch((e) => { console.error('FALHOU', e); process.exit(1); });
