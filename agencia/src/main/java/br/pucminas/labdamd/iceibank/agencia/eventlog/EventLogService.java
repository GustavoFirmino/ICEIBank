/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte B (Relogio de Lamport e registro de eventos)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.eventlog;

import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registra todo evento de uma agencia em duas formas:
 *  - em memoria (lista thread-safe), usada pelo endpoint de historico por
 *    conta (funcionalidade adicional) sem precisar reler o arquivo;
 *  - em um arquivo data/agencia-{id}.jsonl (uma linha JSON por evento), que
 *    e a materia-prima da linha do tempo unificada (MesclarLogs).
 */
@Service
public class EventLogService {

    private static final Logger log = LoggerFactory.getLogger(EventLogService.class);

    private final String nomeAgencia;
    private final Path caminhoArquivo;
    private final ObjectMapper objectMapper;
    private final List<Evento> eventosEmMemoria = new CopyOnWriteArrayList<>();
    private final Object travaArquivo = new Object();

    public EventLogService(AgenciaProperties agenciaProperties, ObjectMapper objectMapper) {
        this.nomeAgencia = "agencia-" + agenciaProperties.id();
        this.caminhoArquivo = Paths.get("data", nomeAgencia + ".jsonl");
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void garantirPastaDeDados() throws IOException {
        Files.createDirectories(caminhoArquivo.getParent());
    }

    public Evento registrar(TipoEvento tipo, long timestampLamport, Long idConta, Map<String, Object> detalhes) {
        Evento evento = new Evento(nomeAgencia, tipo, timestampLamport, Instant.now(), idConta, detalhes);

        eventosEmMemoria.add(evento);
        gravarNoArquivo(evento);

        log.info("[Lamport {}] {} {}", timestampLamport, tipo, detalhes);
        return evento;
    }

    private void gravarNoArquivo(Evento evento) {
        synchronized (travaArquivo) {
            try (Writer writer = Files.newBufferedWriter(
                    caminhoArquivo, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(objectMapper.writeValueAsString(evento));
                writer.write(System.lineSeparator());
            } catch (IOException e) {
                log.error("Falha ao gravar evento no log da agencia", e);
            }
        }
    }

    /**
     * Historico de eventos de uma conta especifica, na ordem em que
     * aconteceram nesta agencia (usado pelo endpoint GET /contas/{id}/historico).
     */
    public List<Evento> historicoDaConta(long idConta) {
        return eventosEmMemoria.stream()
                .filter(e -> e.idConta() != null && e.idConta() == idConta)
                .toList();
    }
}
