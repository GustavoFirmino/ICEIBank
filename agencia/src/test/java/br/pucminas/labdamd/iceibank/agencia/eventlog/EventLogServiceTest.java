/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte B (Registro de eventos)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.eventlog;

import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogServiceTest {

    // Agencia 9 nao existe de verdade - usada so pra nao colidir com o
    // arquivo real gerado por uma execucao normal (data/agencia-0/1/2.jsonl).
    private static final AgenciaProperties AGENCIA_DE_TESTE = new AgenciaProperties(9, 3, 4047, "chave");
    private final Path arquivoDeTeste = Paths.get("data", "agencia-9.jsonl");

    private EventLogService eventLogService;

    @BeforeEach
    void configurar() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Files.createDirectories(arquivoDeTeste.getParent());
        Files.deleteIfExists(arquivoDeTeste); // precisa ser ANTES de construir (o construtor ja abre o arquivo)
        eventLogService = new EventLogService(AGENCIA_DE_TESTE, objectMapper);
    }

    @AfterEach
    void limpar() throws IOException {
        eventLogService.fechar();
        Files.deleteIfExists(arquivoDeTeste);
    }

    @Test
    void registrarGravaUmaLinhaJsonPorEvento() throws IOException {
        eventLogService.registrar(TipoEvento.CRIACAO_CONTA, 1, 5L, Map.of("nomeAluno", "Gustavo"));
        eventLogService.registrar(TipoEvento.DEPOSITO, 2, 5L, Map.of("valor", 100));

        List<String> linhas = Files.readAllLines(arquivoDeTeste);
        assertEquals(2, linhas.size());
        assertTrue(linhas.get(0).contains("CRIACAO_CONTA"));
        assertTrue(linhas.get(1).contains("DEPOSITO"));
    }

    @Test
    void historicoDaContaFiltraApenasEventosDaquelaConta() {
        eventLogService.registrar(TipoEvento.CRIACAO_CONTA, 1, 5L, Map.of());
        eventLogService.registrar(TipoEvento.DEPOSITO, 2, 5L, Map.of("valor", 100));
        eventLogService.registrar(TipoEvento.CRIACAO_CONTA, 3, 8L, Map.of());

        List<Evento> historicoConta5 = eventLogService.historicoDaConta(5);

        assertEquals(2, historicoConta5.size());
        assertEquals(TipoEvento.CRIACAO_CONTA, historicoConta5.get(0).tipo());
        assertEquals(TipoEvento.DEPOSITO, historicoConta5.get(1).tipo());
    }
}
