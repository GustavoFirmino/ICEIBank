/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias) / Sprint 2 - Parte C (Publish/Subscribe)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia;

import br.pucminas.labdamd.iceibank.agencia.clock.RelogioVetorialService;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaNaoEncontradaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.CreditoRemotoNaoAplicavelException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.MensageriaIndisponivelException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.SaldoInsuficienteException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ValorInvalidoException;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.conta.Conta;
import br.pucminas.labdamd.iceibank.agencia.conta.ContaRepository;
import br.pucminas.labdamd.iceibank.agencia.eventlog.Evento;
import br.pucminas.labdamd.iceibank.agencia.eventlog.EventLogService;
import br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento;
import br.pucminas.labdamd.iceibank.agencia.mensageria.CreditoRemotoMensagem;
import br.pucminas.labdamd.iceibank.agencia.mensageria.PublicadorCreditos;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.TransferenciaRequest;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.TransferenciaResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TransferenciaServiceTest {

    // Agencia 93 fake, so para nomear o arquivo de log de teste sem colidir com dados reais.
    private final Path arquivoDeTeste = Paths.get("data", "agencia-93.jsonl");

    private ContaRepository contaRepository;
    private PublicadorFalso publicadorFalso;
    private TransferenciaService transferenciaService;
    private EventLogService eventLog;

    /** Duble de teste simples para PublicadorCreditos - sem broker real, sem framework de mock. */
    private static class PublicadorFalso implements PublicadorCreditos {
        int chamadas = 0;
        java.time.Instant momentoDaChamada;
        final List<Integer> agenciasDestino = new ArrayList<>();
        final List<CreditoRemotoMensagem> mensagens = new ArrayList<>();
        RuntimeException excecaoASerLancada;

        @Override
        public void publicar(int idAgenciaDestino, CreditoRemotoMensagem mensagem) {
            chamadas++;
            momentoDaChamada = java.time.Instant.now();
            try {
                Thread.sleep(15); // o broker "demora" a confirmar; o evento nao pode herdar essa demora
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (excecaoASerLancada != null) {
                throw excecaoASerLancada;
            }
            agenciasDestino.add(idAgenciaDestino);
            mensagens.add(mensagem);
        }
    }

    @BeforeEach
    void configurar() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Files.createDirectories(arquivoDeTeste.getParent());
        Files.deleteIfExists(arquivoDeTeste); // precisa ser ANTES de abrir o EventLogService (que ja abre o arquivo)
        eventLog = new EventLogService(new AgenciaProperties(93, 3, 4047), objectMapper);

        AgenciaProperties agencia0 = new AgenciaProperties(0, 3, 4047);
        contaRepository = new ContaRepository();
        publicadorFalso = new PublicadorFalso();
        transferenciaService = new TransferenciaService(
                agencia0, contaRepository, new RelogioVetorialService(0, 3), eventLog, publicadorFalso, new IdempotencyStore());
    }

    @AfterEach
    void limpar() throws IOException {
        eventLog.fechar();
        Files.deleteIfExists(arquivoDeTeste);
    }

    private void criarConta(long id, long saldoInicial) {
        contaRepository.salvarSeNaoExiste(new Conta(id, "Titular " + id, saldoInicial));
    }

    private CreditoRemotoMensagem mensagemPara(long idConta, long valor, String idMensagem, List<Long> vetorEnvio) {
        return new CreditoRemotoMensagem(idMensagem, idConta, valor, vetorEnvio, 1, 1L);
    }

    // ------------------------------------------------------------------ transferencia local (igual ao Sprint 1)

    @Test
    void transferenciaLocalMovimentaOsSaldosCorretamente() {
        criarConta(0, 100); // agencia 0
        criarConta(3, 10);  // agencia 0 (3 % 3 == 0)

        TransferenciaResponse resposta = transferenciaService.transferir(new TransferenciaRequest(0, 3, 30));

        assertTrue(resposta.mensagem().contains("mesma agencia"));
        assertEquals(70, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(40, contaRepository.buscar(3).orElseThrow().saldo());
        assertEquals(0, publicadorFalso.chamadas, "transferencia local nao deveria publicar mensagem nenhuma");
    }

    @Test
    void transferenciaLocalComSaldoInsuficienteNaoAlteraNadaENaoPublica() {
        criarConta(0, 10);
        criarConta(3, 10);

        assertThrows(SaldoInsuficienteException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 3, 9999)));

        assertEquals(10, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(10, contaRepository.buscar(3).orElseThrow().saldo());
        assertEquals(0, publicadorFalso.chamadas);
    }

    @Test
    void transferenciaLocalComContaDestinoInexistenteReverteODebitoERegistraEstornoNoLog() {
        criarConta(0, 100);

        assertThrows(ContaNaoEncontradaException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 3, 30)));

        assertEquals(100, contaRepository.buscar(0).orElseThrow().saldo());
        var historico = eventLog.historicoDaConta(0);
        assertEquals(2, historico.size(), "deveria ter o debito E o estorno no historico da conta");
        assertEquals(TipoEvento.TRANSFERENCIA_DEBITO, historico.get(0).tipo());
        assertEquals(TipoEvento.TRANSFERENCIA_REVERTIDA, historico.get(1).tipo());
    }

    @Test
    void transferenciaComValorNegativoLancaExcecaoENaoInverteOSentido() {
        criarConta(0, 100);
        criarConta(3, 10);

        assertThrows(ValorInvalidoException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 3, -50)));

        assertEquals(100, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(10, contaRepository.buscar(3).orElseThrow().saldo());
    }

    // ------------------------------------------------------------------ transferencia entre agencias (Sprint 2: mensageria)

    @Test
    void transferenciaEntreAgenciasDebitaOrigemEPublicaAMensagemParaAAgenciaDeDestino() {
        criarConta(0, 100); // agencia 0

        TransferenciaResponse resposta = transferenciaService.transferir(new TransferenciaRequest(0, 1, 20));

        assertTrue(resposta.mensagem().contains("publicada"), "200 agora significa 'o broker aceitou', nao 'ja creditou'");
        assertEquals(80, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(1, publicadorFalso.chamadas);
        assertEquals(List.of(1), publicadorFalso.agenciasDestino);

        CreditoRemotoMensagem mensagem = publicadorFalso.mensagens.get(0);
        assertEquals(1L, mensagem.idConta());
        assertEquals(20L, mensagem.valor());
        assertEquals(0, mensagem.origemAgencia());
        assertEquals(0L, mensagem.idContaOrigem());
        assertNotNull(mensagem.idMensagem());
        // debito local = [1,0,0]; aoEnviar incrementa de novo e o vetor INTEIRO vai na mensagem
        assertEquals(List.of(2L, 0L, 0L), mensagem.vetorEnvio());
    }

    @Test
    void oLogDaOrigemTemDebitoEPublicacaoLigadosPeloIdMensagem() {
        criarConta(0, 100);

        transferenciaService.transferir(new TransferenciaRequest(0, 1, 20));

        List<Evento> historico = eventLog.historicoDaConta(0);
        Evento debito = historico.stream().filter(e -> e.tipo() == TipoEvento.TRANSFERENCIA_DEBITO).findFirst().orElseThrow();
        Evento publicada = historico.stream().filter(e -> e.tipo() == TipoEvento.TRANSFERENCIA_PUBLICADA).findFirst().orElseThrow();
        assertEquals(List.of(1L, 0L, 0L), debito.timestampVetorial());
        assertEquals(List.of(2L, 0L, 0L), publicada.timestampVetorial());
        assertEquals(publicadorFalso.mensagens.get(0).idMensagem(), publicada.detalhes().get("idMensagem"));
    }

    @Test
    void oEventoDePublicacaoCarregaAHoraEmQueOEnvioACONTECEUNaoAHoraEmQueOLogFoiEscrito() {
        // O broker confirma devagar (15 ms no duble) e o consumidor da outra agencia, em outra thread,
        // pode aplicar o credito antes do log da publicacao ser escrito. A hora do evento tem de ser a do
        // envio: senao a linha do tempo por hora de parede mostraria o efeito ANTES da causa.
        criarConta(0, 100);

        transferenciaService.transferir(new TransferenciaRequest(0, 1, 20));

        Evento publicada = eventLog.historicoDaConta(0).stream()
                .filter(e -> e.tipo() == TipoEvento.TRANSFERENCIA_PUBLICADA).findFirst().orElseThrow();
        assertFalse(publicada.horaParede().isAfter(publicadorFalso.momentoDaChamada),
                "PUBLICADA (" + publicada.horaParede() + ") deveria ser anterior a chamada do broker (" + publicadorFalso.momentoDaChamada + ")");
    }

    @Test
    void brokerForaDoArEstornaODebitoEDevolveFalhaRetentavel() {
        criarConta(0, 100);
        publicadorFalso.excecaoASerLancada = new MensageriaIndisponivelException("Connection refused", null);

        assertThrows(MensageriaIndisponivelException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 1, 20)));

        // Diferente do Sprint 1: como NADA foi publicado, o debito e desfeito com seguranca.
        assertEquals(100, contaRepository.buscar(0).orElseThrow().saldo());
        var tipos = eventLog.historicoDaConta(0).stream().map(Evento::tipo).toList();
        assertEquals(List.of(TipoEvento.TRANSFERENCIA_DEBITO, TipoEvento.TRANSFERENCIA_REVERTIDA), tipos);
    }

    @Test
    void retentarComMesmoIdOperacaoDepoisQueOBrokerVoltaFunciona() {
        // Falha retentavel NAO fica presa no cache de idempotencia: se ficasse, o mesmo idOperacao
        // falharia para sempre mesmo depois do broker voltar.
        criarConta(0, 100);
        String idOperacao = "op-broker-caiu";
        publicadorFalso.excecaoASerLancada = new MensageriaIndisponivelException("Connection refused", null);
        assertThrows(MensageriaIndisponivelException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 1, 20, idOperacao)));
        assertEquals(100, contaRepository.buscar(0).orElseThrow().saldo());

        publicadorFalso.excecaoASerLancada = null; // o broker voltou
        TransferenciaResponse resposta = transferenciaService.transferir(new TransferenciaRequest(0, 1, 20, idOperacao));

        assertFalse(resposta.repetida(), "e uma execucao nova, nao um replay do cache");
        assertEquals(80, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(1, publicadorFalso.mensagens.size());
    }

    @Test
    void bugNaoRelacionadoAoBrokerNaoEMascaradoComoMensageriaIndisponivel() {
        // So MensageriaIndisponivelException aciona o estorno - qualquer outro erro (bug de verdade)
        // precisa propagar como esta.
        criarConta(0, 100);
        publicadorFalso.excecaoASerLancada = new IllegalStateException("bug nao relacionado ao broker");

        assertThrows(IllegalStateException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 1, 20)));
    }

    // ------------------------------------------------------------------ idempotencia da requisicao (Sprint 1, mantida)

    @Test
    void transferenciaComMesmoIdOperacaoNaoEAplicadaDuasVezes() {
        criarConta(0, 100);
        criarConta(3, 10);
        String idOperacao = "op-123";

        TransferenciaResponse primeira = transferenciaService.transferir(new TransferenciaRequest(0, 3, 30, idOperacao));
        TransferenciaResponse segunda = transferenciaService.transferir(new TransferenciaRequest(0, 3, 30, idOperacao));

        assertFalse(primeira.repetida());
        assertTrue(segunda.repetida());
        assertEquals(primeira.mensagem(), segunda.mensagem());
        assertEquals(70, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(40, contaRepository.buscar(3).orElseThrow().saldo());
    }

    @Test
    void reenviarUmaTransferenciaEntreAgenciasNaoPublicaAMensagemDeNovo() {
        criarConta(0, 100);
        String idOperacao = "op-entre-agencias";

        transferenciaService.transferir(new TransferenciaRequest(0, 1, 20, idOperacao));
        TransferenciaResponse segunda = transferenciaService.transferir(new TransferenciaRequest(0, 1, 20, idOperacao));

        assertTrue(segunda.repetida());
        assertEquals(1, publicadorFalso.chamadas, "reenvio do cliente nao pode gerar uma segunda mensagem");
        assertEquals(80, contaRepository.buscar(0).orElseThrow().saldo());
    }

    @Test
    void transferenciasSemIdOperacaoSaoAplicadasNormalmente() {
        criarConta(0, 100);
        criarConta(3, 10);

        transferenciaService.transferir(new TransferenciaRequest(0, 3, 10));
        transferenciaService.transferir(new TransferenciaRequest(0, 3, 10));

        assertEquals(80, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(30, contaRepository.buscar(3).orElseThrow().saldo());
    }

    @Test
    void duasThreadsComMesmoIdOperacaoNuncaAplicamAOperacaoDuasVezes() throws InterruptedException {
        criarConta(0, 100);
        criarConta(3, 10);
        String idOperacao = "op-concorrente";
        int totalThreads = 20;

        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch todasProntas = new CountDownLatch(totalThreads);
        CountDownLatch podeComecar = new CountDownLatch(1);

        for (int i = 0; i < totalThreads; i++) {
            pool.submit(() -> {
                todasProntas.countDown();
                try {
                    podeComecar.await();
                    transferenciaService.transferir(new TransferenciaRequest(0, 3, 10, idOperacao));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        todasProntas.await();
        podeComecar.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(90, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(20, contaRepository.buscar(3).orElseThrow().saldo());
    }

    // ------------------------------------------------------------------ consumidor: credito remoto recebido do RabbitMQ

    @Test
    void creditoRemotoAplicaARegra3DoRelogioVetorialECreditaAConta() {
        criarConta(3, 50); // conta da agencia 0 (esta instancia de teste)

        // esta agencia (0) esta em [0,0,0]; a agencia 1 mandou [0,5,2]
        transferenciaService.processarCreditoRemoto(mensagemPara(3, 20, "msg-1", List.of(0L, 5L, 2L)));

        assertEquals(70, contaRepository.buscar(3).orElseThrow().saldo());
        Evento recebimento = eventLog.historicoDaConta(3).get(0);
        assertEquals(TipoEvento.TRANSFERENCIA_CREDITO_REMOTO, recebimento.tipo());
        // max([0,0,0],[0,5,2]) = [0,5,2]; +1 na propria posicao (0) => [1,5,2]
        assertEquals(List.of(1L, 5L, 2L), recebimento.timestampVetorial());
        assertEquals("msg-1", recebimento.detalhes().get("idMensagem"));
    }

    @Test
    void mensagemEntregueDuasVezesCreditaUmaVezSo() {
        criarConta(3, 50);
        CreditoRemotoMensagem mensagem = mensagemPara(3, 20, "msg-duplicada", List.of(0L, 5L, 0L));

        transferenciaService.processarCreditoRemoto(mensagem);
        transferenciaService.processarCreditoRemoto(mensagem); // o broker entregou de novo

        assertEquals(70, contaRepository.buscar(3).orElseThrow().saldo(), "creditou duas vezes: dinheiro criado do nada");
        var tipos = eventLog.historicoDaConta(3).stream().map(Evento::tipo).toList();
        assertEquals(List.of(TipoEvento.TRANSFERENCIA_CREDITO_REMOTO, TipoEvento.CREDITO_REMOTO_DUPLICADO), tipos);
    }

    @Test
    void entregaDuplicadaNaoContaComoEventoCausalNovoNoRelogio() {
        criarConta(3, 50);
        CreditoRemotoMensagem mensagem = mensagemPara(3, 20, "msg-dup-relogio", List.of(0L, 5L, 0L));
        transferenciaService.processarCreditoRemoto(mensagem);
        List<Long> vetorAposPrimeira = eventLog.historicoDaConta(3).get(0).timestampVetorial();

        transferenciaService.processarCreditoRemoto(mensagem);

        Evento duplicado = eventLog.historicoDaConta(3).get(1);
        assertEquals(vetorAposPrimeira, duplicado.timestampVetorial(), "ignorar uma duplicata nao pode avancar o relogio");
    }

    @Test
    void contaInexistenteRejeitaAMensagemMasORecebimentoJaFoiUmEventoDoRelogio() {
        // O cenario da Parte C do roteiro: a agencia reiniciou e perdeu as contas em memoria.
        CreditoRemotoMensagem mensagem = mensagemPara(3, 20, "msg-sem-conta", List.of(0L, 4L, 0L));

        assertThrows(CreditoRemotoNaoAplicavelException.class, () -> transferenciaService.processarCreditoRemoto(mensagem));

        Evento falha = eventLog.historicoDaConta(3).get(0);
        assertEquals(TipoEvento.CREDITO_REMOTO_FALHOU, falha.tipo());
        assertEquals(List.of(1L, 4L, 0L), falha.timestampVetorial(), "receber e um evento (regra 3), mesmo sem conseguir creditar");
        assertEquals("conta nao encontrada nesta agencia", falha.detalhes().get("motivo"));
    }

    @Test
    void mensagemRejeitadaPodeSerReprocessadaDepoisQueAContaExiste() {
        // So marca como "aplicada" DEPOIS de aplicar: a mesma mensagem, reinjetada a partir da
        // dead-letter queue, funciona quando a conta passa a existir - nao e tratada como duplicata.
        CreditoRemotoMensagem mensagem = mensagemPara(3, 20, "msg-reprocessada", List.of(0L, 4L, 0L));
        assertThrows(CreditoRemotoNaoAplicavelException.class, () -> transferenciaService.processarCreditoRemoto(mensagem));

        criarConta(3, 50);
        transferenciaService.processarCreditoRemoto(mensagem);

        assertEquals(70, contaRepository.buscar(3).orElseThrow().saldo());
    }

    @Test
    void vetorDeTamanhoErradoNaMensagemNaoCreditaNada() {
        criarConta(3, 50);

        assertThrows(IllegalArgumentException.class,
                () -> transferenciaService.processarCreditoRemoto(mensagemPara(3, 20, "msg-vetor-ruim", List.of(1L, 2L))));

        assertEquals(50, contaRepository.buscar(3).orElseThrow().saldo());
    }

    @Test
    void vintEntregasSimultaneasDaMesmaMensagemCreditamUmaVezSo() throws InterruptedException {
        criarConta(3, 0);
        CreditoRemotoMensagem mensagem = mensagemPara(3, 10, "msg-concorrente", List.of(0L, 1L, 0L));
        int totalThreads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch largada = new CountDownLatch(1);

        for (int i = 0; i < totalThreads; i++) {
            pool.submit(() -> {
                try {
                    largada.await();
                    transferenciaService.processarCreditoRemoto(mensagem);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        largada.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(10, contaRepository.buscar(3).orElseThrow().saldo());
    }
}
