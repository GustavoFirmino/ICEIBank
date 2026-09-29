/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte C e funcionalidade adicional (dead-letter)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.mensageria;

import br.pucminas.labdamd.iceibank.agencia.clock.RelogioVetorialService;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.conta.Conta;
import br.pucminas.labdamd.iceibank.agencia.conta.ContaRepository;
import br.pucminas.labdamd.iceibank.agencia.eventlog.EventLogService;
import br.pucminas.labdamd.iceibank.agencia.transferencia.IdempotencyStore;
import br.pucminas.labdamd.iceibank.agencia.transferencia.TransferenciaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O que decide se uma mensagem e confirmada (ack) ou vai para a dead-letter queue e a
 * EXCECAO que o listener lanca: nenhuma = ack; AmqpRejectAndDontRequeueException = rejeitada
 * sem reentrega, e o RabbitMQ a encaminha para a DLX configurada na fila.
 */
class CreditoRemotoConsumerTest {

    private final Path arquivoDeTeste = Paths.get("data", "agencia-94.jsonl");

    private ContaRepository contaRepository;
    private EventLogService eventLog;
    private CreditoRemotoConsumer consumidor;

    @BeforeEach
    void configurar() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Files.createDirectories(arquivoDeTeste.getParent());
        Files.deleteIfExists(arquivoDeTeste);
        eventLog = new EventLogService(new AgenciaProperties(94, 3, 4047), objectMapper);
        contaRepository = new ContaRepository();
        var servico = new TransferenciaService(new AgenciaProperties(1, 3, 4047), contaRepository,
                new RelogioVetorialService(1, 3), eventLog, (idAgencia, mensagem) -> { }, new IdempotencyStore());
        consumidor = new CreditoRemotoConsumer(servico);
    }

    @AfterEach
    void limpar() throws IOException {
        eventLog.fechar();
        Files.deleteIfExists(arquivoDeTeste);
    }

    private CreditoRemotoMensagem mensagem(String id, long idConta, long valor) {
        return new CreditoRemotoMensagem(id, idConta, valor, List.of(4L, 0L, 0L), 0, 0);
    }

    @Test
    void creditoAplicavelTerminaSemExcecaoEntaoOBrokerRecebeOAck() {
        contaRepository.salvarSeNaoExiste(new Conta(1, "Bruno", 500));

        assertDoesNotThrow(() -> consumidor.receber(mensagem("m1", 1, 30)));

        assertEquals(530, contaRepository.buscar(1).orElseThrow().saldo());
    }

    @Test
    void contaInexistenteRejeitaSemReentregaEntaoAMensagemVaiParaADeadLetterQueue() {
        // o cenario da Parte C: a agencia reiniciou e perdeu as contas em memoria
        AmqpRejectAndDontRequeueException rejeicao = assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumidor.receber(mensagem("m2", 1, 200)));

        assertTrue(rejeicao.getMessage().contains("Conta 1 nao encontrada"), rejeicao.getMessage());
    }

    @Test
    void entregaDuplicadaDeMensagemJaAplicadaEConfirmadaSemCreditarDeNovo() {
        contaRepository.salvarSeNaoExiste(new Conta(1, "Bruno", 500));
        consumidor.receber(mensagem("m3", 1, 30));

        assertDoesNotThrow(() -> consumidor.receber(mensagem("m3", 1, 30)));

        assertEquals(530, contaRepository.buscar(1).orElseThrow().saldo());
    }
}
