/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte C (API REST/MVC de contas)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.conta;

import br.pucminas.labdamd.iceibank.agencia.clock.RelogioVetorialService;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaDuplicadaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaNaoEncontradaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.DadosInvalidosException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ParticaoInvalidaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.SaldoInsuficienteException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ValorInvalidoException;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ConflitoSincronizacaoException;
import br.pucminas.labdamd.iceibank.agencia.conta.dto.SincronizarRequest;
import br.pucminas.labdamd.iceibank.agencia.conta.dto.ContaResponse;
import br.pucminas.labdamd.iceibank.agencia.conta.dto.CriarContaRequest;
import br.pucminas.labdamd.iceibank.agencia.eventlog.EventLogService;
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

class ContaServiceTest {

    // Agencia 9 (fake, so pra teste) responsavel por contas com id % 3 == 0 (mesmo resto da agencia 0)
    private static final AgenciaProperties AGENCIA_DE_TESTE = new AgenciaProperties(92, 3, 4047);
    private final Path arquivoDeTeste = Paths.get("data", "agencia-92.jsonl");

    private ContaService contaService;
    private EventLogService eventLog;

    @BeforeEach
    void configurar() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Files.createDirectories(arquivoDeTeste.getParent());
        Files.deleteIfExists(arquivoDeTeste); // precisa ser ANTES de construir (o construtor ja abre o arquivo)
        eventLog = new EventLogService(AGENCIA_DE_TESTE, objectMapper);

        // O EventLogService acima usa a "agencia 92" (fake, id proprio desta classe de
        // teste para nao compartilhar arquivo com outras classes de teste) so para
        // nomear o arquivo de log; a logica de particao em si e testada com uma
        // agencia 0 de verdade, que e o que importa para o ContaService.
        AgenciaProperties agencia0DeTeste = new AgenciaProperties(0, 3, 4047);
        contaService = new ContaService(agencia0DeTeste, new ContaRepository(), new RelogioVetorialService(0, 3), eventLog);
    }

    @AfterEach
    void limpar() throws IOException {
        eventLog.fechar();
        Files.deleteIfExists(arquivoDeTeste);
    }

    // --- Sprint 3: sincronizacao offline-first (vetor conhecido x vetor atual) ---

    @Test
    void sincronizacaoComVetorIgualAplicaAOperacao() {
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 100L));
        var conta = contaService.consultarSaldoComVetor(0L);

        var resultado = contaService.sincronizarOperacaoPendente(0L,
                new SincronizarRequest("DEPOSITO", 50L, conta.vetorAtual()));

        assertEquals(150, resultado.saldo());
        assertNotEquals(conta.vetorAtual(), resultado.vetorAtual()); // aplicar a operacao e um evento: o vetor avancou
    }

    @Test
    void sincronizacaoComVetorAntigoDa409ENaoAplica() {
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 100L));
        var antigo = contaService.consultarSaldoComVetor(0L).vetorAtual();
        contaService.depositar(0L, 10L); // mudanca "enquanto o app estava offline"

        ConflitoSincronizacaoException conflito = assertThrows(ConflitoSincronizacaoException.class,
                () -> contaService.sincronizarOperacaoPendente(0L, new SincronizarRequest("SAQUE", 30L, antigo)));

        assertEquals(antigo, conflito.vetorConhecido());
        assertEquals(110, conflito.contaAtual().saldo()); // devolve o estado real
        assertEquals(110, contaService.consultarSaldo(0L).saldo()); // e o saque NAO foi aplicado
    }

    @Test
    void depoisDoConflitoSincronizarComOVetorAtualFunciona() {
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 100L));
        var antigo = contaService.consultarSaldoComVetor(0L).vetorAtual();
        contaService.depositar(0L, 10L);

        var conflito = assertThrows(ConflitoSincronizacaoException.class,
                () -> contaService.sincronizarOperacaoPendente(0L, new SincronizarRequest("SAQUE", 30L, antigo)));
        var resultado = contaService.sincronizarOperacaoPendente(0L,
                new SincronizarRequest("SAQUE", 30L, conflito.vetorAtual()));

        assertEquals(80, resultado.saldo());
    }

    @Test
    void sincronizacaoDeSaqueSemSaldoNaoConsomeTickDoRelogio() {
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 10L));
        var vetor = contaService.consultarSaldoComVetor(0L).vetorAtual();

        assertThrows(SaldoInsuficienteException.class,
                () -> contaService.sincronizarOperacaoPendente(0L, new SincronizarRequest("SAQUE", 99L, vetor)));
        assertEquals(vetor, contaService.consultarSaldoComVetor(0L).vetorAtual());
    }

    @Test
    void sincronizacaoRejeitaVetorAusenteOuDeTamanhoErradoETipoDesconhecido() {
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 10L));
        var vetor = contaService.consultarSaldoComVetor(0L).vetorAtual();

        assertThrows(DadosInvalidosException.class,
                () -> contaService.sincronizarOperacaoPendente(0L, new SincronizarRequest("DEPOSITO", 5L, null)));
        assertThrows(DadosInvalidosException.class,
                () -> contaService.sincronizarOperacaoPendente(0L, new SincronizarRequest("DEPOSITO", 5L, java.util.List.of(1L))));
        assertThrows(DadosInvalidosException.class,
                () -> contaService.sincronizarOperacaoPendente(0L, new SincronizarRequest("PIX", 5L, vetor)));
    }

    @Test
    void sincronizacaoDeContaInexistenteDa404() {
        assertThrows(ContaNaoEncontradaException.class,
                () -> contaService.sincronizarOperacaoPendente(0L, new SincronizarRequest("DEPOSITO", 5L, java.util.List.of(0L, 0L, 0L))));
    }

    @Test
    void criaContaComSucesso() {
        ContaResponse resposta = contaService.criarConta(new CriarContaRequest(0L, "Ana", 100L));
        assertEquals(0, resposta.id());
        assertEquals("Ana", resposta.titular());
        assertEquals(100, resposta.saldo());
    }

    @Test
    void criarContaDuplicadaLancaExcecao() {
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 100L));
        assertThrows(ContaDuplicadaException.class,
                () -> contaService.criarConta(new CriarContaRequest(0L, "Outra", 50L)));
    }

    @Test
    void criarContaForaDaParticaoLancaExcecao() {
        // id=1 pertence a agencia 1 (1 % 3), nao a agencia 0
        assertThrows(ParticaoInvalidaException.class,
                () -> contaService.criarConta(new CriarContaRequest(1L, "Bruno", 0L)));
    }

    @Test
    void consultarContaInexistenteLancaExcecao() {
        assertThrows(ContaNaoEncontradaException.class, () -> contaService.consultarSaldo(42));
    }

    @Test
    void depositarAumentaOSaldo() {
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 100L));
        ContaResponse resposta = contaService.depositar(0, 25);
        assertEquals(125, resposta.saldo());
    }

    @Test
    void sacarComSaldoInsuficienteLancaExcecaoENaoAlteraOSaldo() {
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 100L));
        assertThrows(SaldoInsuficienteException.class, () -> contaService.sacar(0, 9999));
        assertEquals(100, contaService.consultarSaldo(0).saldo());
    }

    @Test
    void sacarComSaldoSuficienteDiminuiOSaldo() {
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 100L));
        ContaResponse resposta = contaService.sacar(0, 40);
        assertEquals(60, resposta.saldo());
    }

    @Test
    void sacarComValorNegativoLancaExcecaoENaoAumentaOSaldo() {
        // Correcao apos revisao de codigo: sem essa validacao, sacar(-1000000)
        // passava direto pelo "saldo < valor" e CREDITAVA a conta.
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 100L));
        assertThrows(ValorInvalidoException.class, () -> contaService.sacar(0, -1_000_000));
        assertEquals(100, contaService.consultarSaldo(0).saldo());
    }

    @Test
    void depositarComValorNegativoLancaExcecaoENaoDiminuiOSaldo() {
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 100L));
        assertThrows(ValorInvalidoException.class, () -> contaService.depositar(0, -1_000_000));
        assertEquals(100, contaService.consultarSaldo(0).saldo());
    }

    @Test
    void criarContaSemIdLancaExcecaoClaraEmVezDeNullPointerException() {
        assertThrows(DadosInvalidosException.class,
                () -> contaService.criarConta(new CriarContaRequest(null, "Ana", 100L)));
    }

    @Test
    void historicoListaOsEventosNaOrdemEmQueAconteceram() {
        // Funcionalidade adicional: historico de transacoes por conta.
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 100L));
        contaService.depositar(0, 25);
        contaService.sacar(0, 10);

        var historico = contaService.historico(0);

        assertEquals(3, historico.size());
        assertEquals("CRIACAO_CONTA", historico.get(0).tipo());
        assertEquals("DEPOSITO", historico.get(1).tipo());
        assertEquals("SAQUE", historico.get(2).tipo());
    }

    @Test
    void historicoDeContaInexistenteLancaExcecao() {
        assertThrows(ContaNaoEncontradaException.class, () -> contaService.historico(999));
    }

    @Test
    void historicoNaoMisturaEventosDeOutraConta() {
        contaService.criarConta(new CriarContaRequest(0L, "Ana", 100L));
        contaService.criarConta(new CriarContaRequest(3L, "Bruno", 50L));
        contaService.depositar(0, 10);

        var historicoConta0 = contaService.historico(0);

        assertEquals(2, historicoConta0.size()); // so criacao + deposito da conta 0
    }
}
