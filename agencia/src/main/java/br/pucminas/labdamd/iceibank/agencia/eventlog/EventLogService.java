/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte B (Relogio de Lamport e registro de eventos)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.eventlog;

import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 *
 * O arquivo e aberto UMA VEZ (no construtor) e mantido aberto pela vida do
 * servico - abrir/fechar o arquivo a cada evento seria um syscall extra por
 * operacao de conta, serializado pelo lock, e seria o gargalo real do
 * caminho de escrita (corrigido apos revisao de codigo).
 */
@Service
public class EventLogService {

    private static final Logger log = LoggerFactory.getLogger(EventLogService.class);

    private final String nomeAgencia;
    private final Path caminhoArquivo;
    private final ObjectMapper objectMapper;
    private final List<Evento> eventosEmMemoria = new CopyOnWriteArrayList<>();
    private final Object travaArquivo = new Object();
    private final BufferedWriter writer;

    public EventLogService(AgenciaProperties agenciaProperties, ObjectMapper objectMapper) {
        this.nomeAgencia = "agencia-" + agenciaProperties.id();
        this.caminhoArquivo = Paths.get("data", nomeAgencia + ".jsonl");
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(caminhoArquivo.getParent());
            this.writer = Files.newBufferedWriter(
                    caminhoArquivo, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao abrir o arquivo de log da " + nomeAgencia, e);
        }
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
            try {
                writer.write(objectMapper.writeValueAsString(evento));
                writer.write(System.lineSeparator());
                writer.flush(); // e um log de auditoria - cada evento precisa estar durável no disco assim que registrado
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

    /** Fecha o arquivo de log. Chamado automaticamente pelo Spring ao desligar a agencia. */
    @PreDestroy
    public void fechar() {
        synchronized (travaArquivo) {
            try {
                writer.close();
            } catch (IOException e) {
                log.warn("Falha ao fechar o arquivo de log da agencia", e);
            }
        }
    }
}
