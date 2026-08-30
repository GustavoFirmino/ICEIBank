/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte E (Linha do tempo unificada)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.ferramentas;

import br.pucminas.labdamd.iceibank.agencia.eventlog.Evento;
import br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MesclarLogsTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private Path pastaDeTeste;

    @AfterEach
    void limpar() throws IOException {
        if (pastaDeTeste != null && Files.exists(pastaDeTeste)) {
            try (var arquivos = Files.walk(pastaDeTeste)) {
                arquivos.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void lerTodosOsEventosJuntaLinhasDeVariosArquivos() throws IOException {
        pastaDeTeste = Files.createTempDirectory("mesclar-logs-teste");
        escreverLinha(pastaDeTeste.resolve("agencia-0.jsonl"), evento("agencia-0", TipoEvento.CRIACAO_CONTA, 1));
        escreverLinha(pastaDeTeste.resolve("agencia-1.jsonl"), evento("agencia-1", TipoEvento.CRIACAO_CONTA, 1));
        escreverLinha(pastaDeTeste.resolve("agencia-1.jsonl"), evento("agencia-1", TipoEvento.DEPOSITO, 2));

        List<Evento> eventos = MesclarLogs.lerTodosOsEventos(pastaDeTeste, objectMapper);

        assertEquals(3, eventos.size());
    }

    @Test
    void ordenarPorLamportRespeitaOTimestampLogicoPrimeiro() {
        Evento e1 = evento("agencia-0", TipoEvento.CRIACAO_CONTA, 5);
        Evento e2 = evento("agencia-1", TipoEvento.CRIACAO_CONTA, 2);
        Evento e3 = evento("agencia-2", TipoEvento.CRIACAO_CONTA, 3);

        List<Evento> ordenados = MesclarLogs.ordenarPorLamport(List.of(e1, e2, e3));

        assertEquals(2, ordenados.get(0).timestampLamport());
        assertEquals(3, ordenados.get(1).timestampLamport());
        assertEquals(5, ordenados.get(2).timestampLamport());
    }

    @Test
    void ordenarPorLamportDesempataPorAgenciaQuandoTimestampEIgual() {
        // Simula o caso real observado: duas agencias diferentes, primeiro
        // evento de cada uma, nunca se falaram = mesmo timestamp logico (1).
        Evento daAgencia2 = new Evento("agencia-2", TipoEvento.CRIACAO_CONTA, 1, Instant.parse("2026-01-01T10:00:00Z"), 2L, Map.of());
        Evento daAgencia0 = new Evento("agencia-0", TipoEvento.CRIACAO_CONTA, 1, Instant.parse("2026-01-01T10:00:00Z"), 0L, Map.of());

        List<Evento> ordenados = MesclarLogs.ordenarPorLamport(List.of(daAgencia2, daAgencia0));

        assertEquals("agencia-0", ordenados.get(0).agencia());
        assertEquals("agencia-2", ordenados.get(1).agencia());
    }

    private void escreverLinha(Path arquivo, Evento evento) throws IOException {
        String linha = "";
        try {
            linha = objectMapper.writeValueAsString(evento) + System.lineSeparator();
        } catch (Exception e) {
            throw new IOException(e);
        }
        Files.writeString(arquivo, linha, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    private Evento evento(String agencia, TipoEvento tipo, long ts) {
        return new Evento(agencia, tipo, ts, Instant.now(), 0L, Map.of());
    }
}
