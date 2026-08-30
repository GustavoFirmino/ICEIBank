/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte C (API REST/MVC de contas)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.conta;

import br.pucminas.labdamd.iceibank.agencia.clock.LamportClockService;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaDuplicadaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaNaoEncontradaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.DadosInvalidosException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ParticaoInvalidaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.SaldoInsuficienteException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ValorInvalidoException;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
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
    private static final AgenciaProperties AGENCIA_DE_TESTE = new AgenciaProperties(92, 3, 4047, "chave");
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
        AgenciaProperties agencia0DeTeste = new AgenciaProperties(0, 3, 4047, "chave");
        contaService = new ContaService(agencia0DeTeste, new ContaRepository(), new LamportClockService(), eventLog);
    }

    @AfterEach
    void limpar() throws IOException {
        eventLog.fechar();
        Files.deleteIfExists(arquivoDeTeste);
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
}
