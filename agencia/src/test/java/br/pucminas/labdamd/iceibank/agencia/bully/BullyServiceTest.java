/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 3 - Partes A/B/C (Bully)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.bully;

import br.pucminas.labdamd.iceibank.agencia.clock.RelogioVetorialService;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.config.BullyProperties;
import br.pucminas.labdamd.iceibank.agencia.eventlog.EventLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa a logica de decisao do Bully de UMA agencia, isolada. As portas usadas (portaBase 59000+) nao tem
 * nenhum servidor, entao "as outras agencias" estao todas fora do ar - cenario do heartbeat/eleicao.
 */
class BullyServiceTest {

    private static final int PORTA_BASE_SEM_SERVIDOR = 59000;
    private EventLogService eventLog;

    private BullyService criar(int id) throws IOException {
        // ids 80+ so para nao compartilhar arquivo de log com outros testes
        AgenciaProperties props = new AgenciaProperties(id, 3, PORTA_BASE_SEM_SERVIDOR);
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        Files.createDirectories(Paths.get("data"));
        Files.deleteIfExists(Paths.get("data", "agencia-" + id + ".jsonl"));
        eventLog = new EventLogService(props, om);
        return new BullyService(props, new BullyProperties(false, 100, 200), new RelogioVetorialService(id % 3, 3), eventLog, om);
    }

    @AfterEach
    void limpar() throws IOException {
        if (eventLog != null) {
            eventLog.fechar();
        }
        for (int id : new int[]{80, 81, 82}) {
            Files.deleteIfExists(Paths.get("data", "agencia-" + id + ".jsonl"));
        }
    }

    @Test
    void agenciaDeMaiorIdSemNinguemMaiorVenceSozinha() throws IOException {
        // id%3==2 no relogio, mas o id "logico" na propriedade e 82 -> nao ha agencia com id maior no intervalo [83,3)
        BullyService bully = criar(82);
        assertNull(bully.obterLiderAtual());
        bully.iniciarEleicao();
        assertEquals(82, bully.obterLiderAtual());
    }

    @Test
    void agenciaMenorSemRespostaDasMaioresTambemSeTornaLider() throws IOException {
        // Com id 0 e total 3, as agencias 1 e 2 estao em localhost:59001/59002 - ninguem responde.
        AgenciaProperties props = new AgenciaProperties(0, 3, PORTA_BASE_SEM_SERVIDOR);
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        Files.createDirectories(Paths.get("data"));
        Files.deleteIfExists(Paths.get("data", "agencia-0.jsonl"));
        eventLog = new EventLogService(props, om);
        BullyService bully = new BullyService(props, new BullyProperties(false, 100, 200),
                new RelogioVetorialService(0, 3), eventLog, om);

        bully.iniciarEleicao();

        assertEquals(0, bully.obterLiderAtual());
        eventLog.fechar();
        Files.deleteIfExists(Paths.get("data", "agencia-0.jsonl"));
    }

    @Test
    void receberCoordenadorAtualizaALider() throws IOException {
        BullyService bully = criar(81);
        bully.receberCoordenador(2, List.of(0L, 0L, 5L));
        assertEquals(2, bully.obterLiderAtual());
    }

    @Test
    void heartbeatComLiderForaDoArEsqueceALiderEReelege() throws IOException {
        AgenciaProperties props = new AgenciaProperties(1, 3, PORTA_BASE_SEM_SERVIDOR);
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        Files.createDirectories(Paths.get("data"));
        Files.deleteIfExists(Paths.get("data", "agencia-1.jsonl"));
        eventLog = new EventLogService(props, om);
        BullyService bully = new BullyService(props, new BullyProperties(false, 100, 200),
                new RelogioVetorialService(1, 3), eventLog, om);
        bully.receberCoordenador(2, List.of(0L, 0L, 1L)); // lider conhecida: 2 (que nao esta no ar)

        bully.verificarLider();

        // 2 nao respondeu -> foi esquecida -> eleicao: ninguem maior responde -> a 1 assume
        assertEquals(1, bully.obterLiderAtual());
        eventLog.fechar();
        Files.deleteIfExists(Paths.get("data", "agencia-1.jsonl"));
    }

    @Test
    void heartbeatSendoEuALiderNaoFazNada() throws IOException {
        BullyService bully = criar(80);
        bully.iniciarEleicao();
        Integer antes = bully.obterLiderAtual();
        bully.verificarLider();
        assertEquals(antes, bully.obterLiderAtual());
    }
}
