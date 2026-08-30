/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte B (Relogio de Lamport)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.clock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LamportClockServiceTest {

    @Test
    void eventoLocalIncrementaUmAUm() {
        LamportClockService relogio = new LamportClockService();
        assertEquals(1, relogio.eventoLocal());
        assertEquals(2, relogio.eventoLocal());
        assertEquals(3, relogio.eventoLocal());
    }

    @Test
    void aoEnviarTambemIncrementa() {
        LamportClockService relogio = new LamportClockService();
        relogio.eventoLocal(); // contador = 1
        assertEquals(2, relogio.aoEnviar());
    }

    @Test
    void aoReceberAdotaOMaiorMaisUm_quandoRecebidoEMaior() {
        LamportClockService relogio = new LamportClockService();
        relogio.eventoLocal(); // contador = 1
        // mensagem chegou de uma agencia mais adiantada (timestamp 10)
        assertEquals(11, relogio.aoReceber(10));
    }

    @Test
    void aoReceberIgnoraOTimestampRecebido_quandoLocalJaEMaior() {
        LamportClockService relogio = new LamportClockService();
        for (int i = 0; i < 10; i++) relogio.eventoLocal(); // contador = 10
        // mensagem chegou de uma agencia mais atrasada (timestamp 3)
        assertEquals(11, relogio.aoReceber(3));
    }

    @Test
    void contadorNuncaAndaParaTras() {
        LamportClockService relogio = new LamportClockService();
        relogio.aoReceber(100);
        assertEquals(101, relogio.valorAtual());
        relogio.eventoLocal();
        assertEquals(102, relogio.valorAtual());
    }

    @Test
    void incrementosConcorrentesNaoSePerdem() throws InterruptedException {
        LamportClockService relogio = new LamportClockService();
        int totalThreads = 20;
        int chamadasPorThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        AtomicInteger prontos = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            pool.submit(() -> {
                for (int j = 0; j < chamadasPorThread; j++) {
                    relogio.eventoLocal();
                }
                prontos.incrementAndGet();
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(totalThreads, prontos.get());
        // Se o "synchronized" nao existisse, threads concorrentes perderiam
        // incrementos (condicao de corrida) e o total ficaria menor que isso.
        assertEquals((long) totalThreads * chamadasPorThread, relogio.valorAtual());
    }
}
