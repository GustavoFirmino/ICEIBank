/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Funcionalidade adicional (dead-letter queue com reprocessamento)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.mensageria;

import br.pucminas.labdamd.iceibank.agencia.clock.RelogioVetorialService;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.MensageriaIndisponivelException;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.eventlog.Evento;
import br.pucminas.labdamd.iceibank.agencia.eventlog.EventLogService;
import br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento;
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

import static org.junit.jupiter.api.Assertions.*;

class MensagensMortasServiceTest {

    private final Path arquivoDeTeste = Paths.get("data", "agencia-95.jsonl");

    private FilaFalsa fila;
    private PublicadorFalso publicador;
    private EventLogService eventLog;
    private MensagensMortasService servico;

    /** Fila em memoria que respeita o contrato: so sai da fila o que o visitante manda CONFIRMAR. */
    private static class FilaFalsa implements FilaDeMensagensMortas {
        final List<CreditoRemotoMensagem> mensagens = new ArrayList<>();

        @Override
        public void percorrer(int max, Visitante visitante) {
            List<CreditoRemotoMensagem> instantaneo = new ArrayList<>(mensagens);
            for (CreditoRemotoMensagem mensagem : instantaneo.subList(0, Math.min(max, instantaneo.size()))) {
                if (visitante.visitar(mensagem) == Decisao.CONFIRMAR) {
                    mensagens.remove(mensagem);
                }
            }
        }
    }

    private static class PublicadorFalso implements PublicadorCreditos {
        final List<CreditoRemotoMensagem> publicadas = new ArrayList<>();
        final List<Integer> destinos = new ArrayList<>();
        boolean brokerForaDoAr = false;

        @Override
        public void publicar(int idAgenciaDestino, CreditoRemotoMensagem mensagem) {
            if (brokerForaDoAr) {
                throw new MensageriaIndisponivelException("Connection refused", null);
            }
            publicadas.add(mensagem);
            destinos.add(idAgenciaDestino);
        }
    }

    @BeforeEach
    void configurar() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Files.createDirectories(arquivoDeTeste.getParent());
        Files.deleteIfExists(arquivoDeTeste);
        eventLog = new EventLogService(new AgenciaProperties(95, 3, 4047), objectMapper);
        fila = new FilaFalsa();
        publicador = new PublicadorFalso();
        // esta instancia de teste e a agencia 1 (contas 1, 4, 7, ...)
        servico = new MensagensMortasService(new AgenciaProperties(1, 3, 4047), fila, publicador, eventLog,
                new RelogioVetorialService(1, 3));
    }

    @AfterEach
    void limpar() throws IOException {
        eventLog.fechar();
        Files.deleteIfExists(arquivoDeTeste);
    }

    private CreditoRemotoMensagem morta(String id, long idConta, long valor) {
        return new CreditoRemotoMensagem(id, idConta, valor, List.of(6L, 0L, 0L), 0, 0);
    }

    @Test
    void listarMostraSoAsMensagensCujaContaDeDestinoEDestaAgenciaENaoRemoveNada() {
        fila.mensagens.add(morta("m-conta1", 1, 200));   // agencia 1: e desta
        fila.mensagens.add(morta("m-conta2", 2, 50));    // agencia 2: nao e
        fila.mensagens.add(morta("m-conta4", 4, 10));    // agencia 1: e desta

        List<CreditoRemotoMensagem> listadas = servico.listar();

        assertEquals(List.of("m-conta1", "m-conta4"), listadas.stream().map(CreditoRemotoMensagem::idMensagem).toList());
        assertEquals(3, fila.mensagens.size(), "listar nao pode consumir mensagens da dead-letter queue");
    }

    @Test
    void reprocessarRepublicaAsDestaAgenciaERetiraDaDeadLetterQueue() {
        fila.mensagens.add(morta("m-conta1", 1, 200));

        var resultado = servico.reprocessar();

        assertEquals(1, resultado.reprocessadas());
        assertEquals(0, resultado.pendentes());
        assertEquals(List.of("m-conta1"), publicador.publicadas.stream().map(CreditoRemotoMensagem::idMensagem).toList());
        assertEquals(List.of(1), publicador.destinos, "republica para a agencia dona da conta");
        assertTrue(fila.mensagens.isEmpty());
    }

    @Test
    void reprocessarPreservaOIdMensagemEOsDadosOriginais() {
        // o idMensagem original e o que permite ao consumidor deduplicar: uma mensagem reprocessada
        // continua sendo A MESMA mensagem, nao uma nova.
        CreditoRemotoMensagem original = morta("m-original", 1, 200);
        fila.mensagens.add(original);

        servico.reprocessar();

        assertEquals(original, publicador.publicadas.get(0));
    }

    @Test
    void reprocessarNaoToucaMensagensDeOutrasAgencias() {
        fila.mensagens.add(morta("m-conta2", 2, 50)); // e da agencia 2

        var resultado = servico.reprocessar();

        assertEquals(0, resultado.reprocessadas());
        assertTrue(publicador.publicadas.isEmpty());
        assertEquals(1, fila.mensagens.size(), "continua na dead-letter queue para a agencia 2 tratar");
    }

    @Test
    void seOBrokerCairDuranteOReprocessamentoAMensagemContinuaNaDeadLetterQueue() {
        fila.mensagens.add(morta("m-conta1", 1, 200));
        publicador.brokerForaDoAr = true;

        var resultado = servico.reprocessar();

        assertEquals(0, resultado.reprocessadas());
        assertEquals(1, resultado.pendentes());
        assertEquals(1, fila.mensagens.size(), "so sai da dead-letter queue depois que a republicacao foi confirmada");
    }

    @Test
    void cadaReprocessamentoViraEventoLocalNoLogParaAuditoria() {
        fila.mensagens.add(morta("m-conta1", 1, 200));

        servico.reprocessar();

        Evento evento = eventLog.historicoDaConta(1).get(0);
        assertEquals(TipoEvento.CREDITO_REMOTO_REPROCESSADO, evento.tipo());
        assertEquals("m-conta1", evento.detalhes().get("idMensagem"));
        assertEquals(List.of(0L, 1L, 0L), evento.timestampVetorial(), "e um evento local desta agencia (regra 1)");
    }

    @Test
    void reprocessarSemNadaNaFilaNaoFazNada() {
        var resultado = servico.reprocessar();

        assertEquals(0, resultado.reprocessadas());
        assertEquals(0, resultado.pendentes());
    }
}
