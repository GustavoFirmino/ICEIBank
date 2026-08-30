/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia;

import br.pucminas.labdamd.iceibank.agencia.clock.LamportClockService;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.AgenciaDestinoIndisponivelException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ComunicacaoAgenciaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaNaoEncontradaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.SaldoInsuficienteException;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.conta.Conta;
import br.pucminas.labdamd.iceibank.agencia.conta.ContaRepository;
import br.pucminas.labdamd.iceibank.agencia.eventlog.EventLogService;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.CreditarRemotoResponse;
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

import static org.junit.jupiter.api.Assertions.*;

class TransferenciaServiceTest {

    // Agencia 9 fake, so para nomear o arquivo de log de teste sem colidir com dados reais.
    private final Path arquivoDeTeste = Paths.get("data", "agencia-93.jsonl");

    private ContaRepository contaRepository;
    private RemoteBranchClientFalso remoteBranchClientFalso;
    private TransferenciaService transferenciaService;
    private EventLogService eventLog;

    /** Duble de teste simples para RemoteBranchClient - sem HTTP real, sem framework de mock. */
    private static class RemoteBranchClientFalso implements RemoteBranchClient {
        int chamadas = 0;
        Integer ultimaAgenciaDestino;
        Long ultimaContaDestino;
        Long ultimoValor;
        Integer ultimaOrigemAgencia;
        RuntimeException excecaoASerLancada;

        @Override
        public void creditarRemoto(int idAgenciaDestino, long idConta, long valor, long timestampLamport, int origemAgencia) {
            chamadas++;
            ultimaAgenciaDestino = idAgenciaDestino;
            ultimaContaDestino = idConta;
            ultimoValor = valor;
            ultimaOrigemAgencia = origemAgencia;
            if (excecaoASerLancada != null) {
                throw excecaoASerLancada;
            }
        }
    }

    @BeforeEach
    void configurar() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Files.createDirectories(arquivoDeTeste.getParent());
        Files.deleteIfExists(arquivoDeTeste); // precisa ser ANTES de abrir o EventLogService (que ja abre o arquivo)
        eventLog = new EventLogService(new AgenciaProperties(93, 3, 4047, "chave"), objectMapper);

        AgenciaProperties agencia0 = new AgenciaProperties(0, 3, 4047, "chave-interna-teste");
        contaRepository = new ContaRepository();
        remoteBranchClientFalso = new RemoteBranchClientFalso();
        transferenciaService = new TransferenciaService(
                agencia0, contaRepository, new LamportClockService(), eventLog, remoteBranchClientFalso, new IdempotencyStore());
    }

    @AfterEach
    void limpar() throws IOException {
        eventLog.fechar();
        Files.deleteIfExists(arquivoDeTeste);
    }

    private void criarConta(long id, long saldoInicial) {
        contaRepository.salvarSeNaoExiste(new Conta(id, "Titular " + id, saldoInicial));
    }

    @Test
    void transferenciaLocalMovimentaOsSaldosCorretamente() {
        criarConta(0, 100); // agencia 0
        criarConta(3, 10);  // agencia 0 (3 % 3 == 0)

        TransferenciaResponse resposta = transferenciaService.transferir(new TransferenciaRequest(0, 3, 30));

        assertTrue(resposta.mensagem().contains("mesma agencia"));
        assertEquals(70, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(40, contaRepository.buscar(3).orElseThrow().saldo());
        assertEquals(0, remoteBranchClientFalso.chamadas, "transferencia local nao deveria chamar outra agencia");
    }

    @Test
    void transferenciaLocalComSaldoInsuficienteNaoAlteraNadaENaoChamaRede() {
        criarConta(0, 10);
        criarConta(3, 10);

        assertThrows(SaldoInsuficienteException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 3, 9999)));

        assertEquals(10, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(10, contaRepository.buscar(3).orElseThrow().saldo());
        assertEquals(0, remoteBranchClientFalso.chamadas);
    }

    @Test
    void transferenciaLocalComContaDestinoInexistenteReverteODebito() {
        criarConta(0, 100);

        assertThrows(ContaNaoEncontradaException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 3, 30)));

        // conta de destino nao existe, mas e tudo local (mesma JVM) - o debito
        // deve ter sido revertido, diferente do caso entre agencias.
        assertEquals(100, contaRepository.buscar(0).orElseThrow().saldo());
    }

    @Test
    void transferenciaEntreAgenciasComSucessoDebitaOrigemEChamaAgenciaDestino() {
        criarConta(0, 100); // agencia 0

        TransferenciaResponse resposta = transferenciaService.transferir(new TransferenciaRequest(0, 1, 20));

        assertTrue(resposta.mensagem().contains("entre agencias"));
        assertEquals(80, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(1, remoteBranchClientFalso.chamadas);
        assertEquals(1, remoteBranchClientFalso.ultimaAgenciaDestino);
        assertEquals(1L, remoteBranchClientFalso.ultimaContaDestino);
        assertEquals(20L, remoteBranchClientFalso.ultimoValor);
        assertEquals(0, remoteBranchClientFalso.ultimaOrigemAgencia);
    }

    @Test
    void transferenciaComMesmoIdOperacaoNaoEAplicadaDuasVezes() {
        // Funcionalidade adicional: idempotencia.
        criarConta(0, 100);
        criarConta(3, 10);
        String idOperacao = "op-123";

        TransferenciaResponse primeira = transferenciaService.transferir(
                new TransferenciaRequest(0, 3, 30, idOperacao));
        TransferenciaResponse segunda = transferenciaService.transferir(
                new TransferenciaRequest(0, 3, 30, idOperacao));

        assertFalse(primeira.repetida());
        assertTrue(segunda.repetida());
        assertEquals(primeira.mensagem(), segunda.mensagem());
        // o saldo so foi debitado/creditado UMA vez, nao duas
        assertEquals(70, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(40, contaRepository.buscar(3).orElseThrow().saldo());
    }

    @Test
    void transferenciasComIdOperacaoDiferenteOuAusenteSaoAplicadasNormalmente() {
        criarConta(0, 100);
        criarConta(3, 10);

        // duas transferencias SEM idOperacao (uso normal, sem idempotencia) - ambas aplicam
        transferenciaService.transferir(new TransferenciaRequest(0, 3, 10));
        transferenciaService.transferir(new TransferenciaRequest(0, 3, 10));

        assertEquals(80, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(30, contaRepository.buscar(3).orElseThrow().saldo());
    }

    @Test
    void retentarComMesmoIdOperacaoAposFalhaNaoDebitaDeNovo() {
        // Correcao apos revisao de codigo: a primeira versao da idempotencia
        // so cacheava SUCESSOS - um retry apos a falha conhecida (Parte D)
        // debitava a conta de novo a cada tentativa, o oposto do esperado.
        criarConta(0, 100);
        String idOperacao = "op-com-falha";
        remoteBranchClientFalso.excecaoASerLancada = new ComunicacaoAgenciaException("Connection refused", null);

        // primeira tentativa: falha (debito local ja e aplicado, limitacao conhecida)
        assertThrows(AgenciaDestinoIndisponivelException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 1, 20, idOperacao)));
        assertEquals(80, contaRepository.buscar(0).orElseThrow().saldo());

        // segunda tentativa (retry do cliente, mesmo idOperacao): relanca a
        // MESMA falha, sem debitar de novo e sem chamar a agencia remota outra vez
        assertThrows(AgenciaDestinoIndisponivelException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 1, 20, idOperacao)));
        assertEquals(80, contaRepository.buscar(0).orElseThrow().saldo(), "nao pode debitar duas vezes no retry");
        assertEquals(1, remoteBranchClientFalso.chamadas, "a segunda tentativa nao deveria nem chamar a rede");
    }

    @Test
    void duasThreadsComMesmoIdOperacaoNuncaAplicamAOperacaoDuasVezes() throws InterruptedException {
        // Correcao apos revisao de codigo: buscar()-depois-registrar() nao era
        // atomico, entao duas requisicoes concorrentes com o mesmo idOperacao
        // podiam ambas passar pelo cache-miss e debitar duas vezes.
        criarConta(0, 100);
        criarConta(3, 10);
        String idOperacao = "op-concorrente";
        int totalThreads = 20;

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(totalThreads);
        java.util.concurrent.CountDownLatch todasProntas = new java.util.concurrent.CountDownLatch(totalThreads);
        java.util.concurrent.CountDownLatch podeComecar = new java.util.concurrent.CountDownLatch(1);

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
        podeComecar.countDown(); // libera todas as threads de uma vez, maximizando a chance de corrida
        pool.shutdown();
        pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // Se a operacao tivesse sido aplicada mais de uma vez, o saldo teria caido mais que 10.
        assertEquals(90, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(20, contaRepository.buscar(3).orElseThrow().saldo());
    }

    @Test
    void transferenciaEntreAgenciasComFalhaNaoReverteODebito_limitacaoConhecida() {
        criarConta(0, 100);
        remoteBranchClientFalso.excecaoASerLancada = new ComunicacaoAgenciaException("Connection refused", null);

        AgenciaDestinoIndisponivelException excecao = assertThrows(AgenciaDestinoIndisponivelException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 1, 20)));

        assertTrue(excecao.getMessage().contains("Debito ja aplicado"));
        // Esta e a limitacao conhecida do Sprint 1: o debito NAO e revertido.
        assertEquals(80, contaRepository.buscar(0).orElseThrow().saldo());
    }

    @Test
    void bugNaoRelacionadoARedeNaoEMascaradoComoAgenciaIndisponivel() {
        // Correcao apos revisao de codigo: so ComunicacaoAgenciaException deve
        // virar "agencia indisponivel" - qualquer outro RuntimeException (ex.:
        // um bug de verdade em RemoteBranchClient) precisa propagar como esta.
        criarConta(0, 100);
        remoteBranchClientFalso.excecaoASerLancada = new IllegalStateException("bug nao relacionado a rede");

        assertThrows(IllegalStateException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 1, 20)));
    }

    @Test
    void transferenciaComValorNegativoLancaExcecaoENaoInverteOSentido() {
        criarConta(0, 100);
        criarConta(3, 10);

        assertThrows(br.pucminas.labdamd.iceibank.agencia.common.exceptions.ValorInvalidoException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 3, -50)));

        assertEquals(100, contaRepository.buscar(0).orElseThrow().saldo());
        assertEquals(10, contaRepository.buscar(3).orElseThrow().saldo());
    }

    @Test
    void transferenciaLocalComContaDestinoInexistenteRegistraEstornoNoLog() {
        criarConta(0, 100);

        assertThrows(ContaNaoEncontradaException.class,
                () -> transferenciaService.transferir(new TransferenciaRequest(0, 3, 30)));

        var historico = eventLog.historicoDaConta(0);
        assertEquals(2, historico.size(), "deveria ter o debito E o estorno no historico da conta");
        assertEquals(br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento.TRANSFERENCIA_DEBITO, historico.get(0).tipo());
        assertEquals(br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento.TRANSFERENCIA_REVERTIDA, historico.get(1).tipo());
    }

    @Test
    void creditarRemotoAplicaRegraDeReceberDoRelogioDeLamport() {
        criarConta(1, 50);

        // a "agencia 0" (fake) esta bem adiantada (timestamp 100) - o
        // creditarRemoto precisa ajustar o relogio local para max(0,100)+1=101
        CreditarRemotoResponse resposta = transferenciaService.creditarRemoto(1, 20, 100, 0);

        assertEquals(70, resposta.saldoAtual());
        assertEquals(70, contaRepository.buscar(1).orElseThrow().saldo());
    }
}
