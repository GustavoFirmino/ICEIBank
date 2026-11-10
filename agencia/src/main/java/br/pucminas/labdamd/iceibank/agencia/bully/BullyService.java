/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 3 - Parte A (Algoritmo de Bully: eleicao e coordenador)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.bully;

import br.pucminas.labdamd.iceibank.agencia.clock.RelogioVetorialService;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.config.BullyProperties;
import br.pucminas.labdamd.iceibank.agencia.eventlog.EventLogService;
import br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Algoritmo de Bully (Garcia-Molina, 1982): a agencia ATIVA de maior ID e sempre a lider.
 *
 * Eleicao iniciada por uma agencia:
 *  1. manda "eleicao" a todas as agencias de ID MAIOR que o dela;
 *  2. se NENHUMA responde, ela vence: vira lider e anuncia a todas (mensagem de coordenador);
 *  3. se alguma de ID maior responde, essa assume a conducao (ela tambem inicia uma eleicao) - quem
 *     perguntou apenas espera o anuncio.
 *
 * Ao RECEBER uma eleicao de alguem de ID menor, a agencia responde (o HTTP 200 e o "estou viva") e inicia
 * a propria eleicao. Isso gera uma "tempestade" de eleicoes na subida, mas converge sempre para o maior ID.
 *
 * Concorrencia: liderAtual e lido por threads HTTP (Tomcat) e escrito pelo heartbeat/eleicoes, entao e
 * volatile; "eleicaoEmAndamento" e um AtomicBoolean (compareAndSet impede duas eleicoes simultaneas nesta
 * agencia). O trabalho de eleicao roda em um executor proprio - se rodasse na thread que atendeu o
 * POST /bully/eleicao, a resposta so sairia depois da eleicao inteira e estouraria o timeout de quem perguntou.
 *
 * A lider eleita e, neste sprint, apenas informativa; no Sprint 4 ela coordenara as transacoes (2PC).
 */
@Service
public class BullyService {

    private static final Logger log = LoggerFactory.getLogger(BullyService.class);

    private final AgenciaProperties agencia;
    private final BullyProperties propriedades;
    private final RelogioVetorialService relogio;
    private final EventLogService eventLog;
    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "bully-worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean eleicaoEmAndamento = new AtomicBoolean(false);

    /** null = nenhuma lider conhecida. */
    private volatile Integer liderAtual = null;

    public BullyService(AgenciaProperties agencia, BullyProperties propriedades, RelogioVetorialService relogio,
                        EventLogService eventLog, ObjectMapper objectMapper) {
        this.agencia = agencia;
        this.propriedades = propriedades;
        this.relogio = relogio;
        this.eventLog = eventLog;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(propriedades.timeoutMs())).build();
    }

    /** Ao terminar de subir, cada agencia ja dispara a propria eleicao para descobrir (ou estabelecer) a lider. */
    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        if (!propriedades.habilitado()) {
            log.info("Bully desabilitado (bully.habilitado=false).");
            return;
        }
        executor.submit(this::iniciarEleicao);
    }

    @PreDestroy
    public void encerrar() {
        executor.shutdownNow();
    }

    public Integer obterLiderAtual() {
        return liderAtual;
    }

    public int idAgencia() {
        return agencia.id();
    }

    // --- eleicao (lado que inicia) ------------------------------------------------------------------------

    public void iniciarEleicao() {
        if (!eleicaoEmAndamento.compareAndSet(false, true)) {
            return; // ja existe uma eleicao em andamento nesta agencia
        }
        boolean alguemRespondeu = false;
        try {
            eventLog.registrar(TipoEvento.ELEICAO_INICIADA, relogio.eventoLocal(), null,
                    Map.of("idAgencia", agencia.id()));

            for (int id = agencia.id() + 1; id < agencia.totalAgencias(); id++) {
                // O vetor viaja na mensagem (regra 2). Se a agencia maior estiver fora do ar, o envio conta
                // igual: o relogio so registra que a mensagem FOI enviada.
                List<Long> vetorEnvio = relogio.aoEnviar();
                if (postar(id, "/bully/eleicao", Map.of("origemId", agencia.id(), "vetorEnvio", vetorEnvio))) {
                    alguemRespondeu = true;
                }
            }
        } finally {
            eleicaoEmAndamento.set(false);
        }

        if (!alguemRespondeu) {
            tornarMeLider();
        }
        // Se alguem maior respondeu, ELA conduz a propria eleicao e anuncia o resultado em /bully/coordenador.
    }

    private void tornarMeLider() {
        liderAtual = agencia.id();
        eventLog.registrar(TipoEvento.TORNOU_LIDER, relogio.eventoLocal(), null, Map.of("idAgencia", agencia.id()));
        for (int id = 0; id < agencia.totalAgencias(); id++) {
            if (id == agencia.id()) {
                continue;
            }
            List<Long> vetorEnvio = relogio.aoEnviar();
            postar(id, "/bully/coordenador", Map.of("liderId", agencia.id(), "vetorEnvio", vetorEnvio));
            // agencia fora do ar: descobrira a lider quando voltar (ela inicia uma eleicao ao subir)
        }
    }

    // --- eleicao (lado que recebe) ------------------------------------------------------------------------

    /** Chamado pelo POST /bully/eleicao. O 200 da rota e o "OK, estou viva"; a eleicao propria roda em segundo plano. */
    public void receberEleicao(int origemId, List<Long> vetorRecebido) {
        List<Long> vetor = relogio.aoReceber(vetorRecebido);
        eventLog.registrar(TipoEvento.ELEICAO_RECEBIDA, vetor, null,
                Map.of("de", origemId, "minhaId", agencia.id()));
        if (propriedades.habilitado()) {
            executor.submit(this::iniciarEleicao); // tenho ID maior que quem perguntou: tambem sou candidata
        }
    }

    /** Chamado pelo POST /bully/coordenador. */
    public void receberCoordenador(int liderId, List<Long> vetorRecebido) {
        liderAtual = liderId;
        List<Long> vetor = relogio.aoReceber(vetorRecebido);
        eventLog.registrar(TipoEvento.LIDER_ANUNCIADO, vetor, null,
                Map.of("liderId", liderId, "agencia", agencia.id()));
    }

    // --- HTTP ---------------------------------------------------------------------------------------------

    /** POST JSON em outra agencia. true = respondeu 2xx dentro do timeout; false = fora do ar / lenta / erro. */
    private boolean postar(int idDestino, String caminho, Map<String, Object> corpo) {
        try {
            HttpRequest requisicao = HttpRequest.newBuilder(URI.create(agencia.urlDaAgencia(idDestino) + caminho))
                    .timeout(Duration.ofMillis(propriedades.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(corpo)))
                    .build();
            HttpResponse<Void> resposta = http.send(requisicao, HttpResponse.BodyHandlers.discarding());
            return resposta.statusCode() / 100 == 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
