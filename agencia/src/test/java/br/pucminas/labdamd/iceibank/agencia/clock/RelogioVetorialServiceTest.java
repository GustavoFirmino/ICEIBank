/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte B (Relogio vetorial)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.clock;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RelogioVetorialServiceTest {

    @Test
    void comecaZerado() {
        assertEquals(List.of(0L, 0L, 0L), new RelogioVetorialService(1, 3).valorAtual());
    }

    @Test
    void regra1EventoLocalIncrementaSoAPropriaPosicao() {
        RelogioVetorialService agencia1 = new RelogioVetorialService(1, 3);

        assertEquals(List.of(0L, 1L, 0L), agencia1.eventoLocal());
        assertEquals(List.of(0L, 2L, 0L), agencia1.eventoLocal());
    }

    @Test
    void regra2AoEnviarIncrementaEDevolveOVetorInteiroParaAnexarAMensagem() {
        RelogioVetorialService agencia0 = new RelogioVetorialService(0, 3);
        agencia0.eventoLocal(); // debito local -> [1,0,0]

        assertEquals(List.of(2L, 0L, 0L), agencia0.aoEnviar());
    }

    @Test
    void regra3AoReceberFazMaxPosicaoAPosicaoEIncrementaAPropria() {
        RelogioVetorialService agencia1 = new RelogioVetorialService(1, 3);
        agencia1.eventoLocal();
        agencia1.eventoLocal(); // [0,2,0]

        // a agencia 0 ja fez 3 eventos e viu a agencia 2 fazer 1: manda [3,0,1]
        List<Long> aposReceber = agencia1.aoReceber(List.of(3L, 0L, 1L));

        // max([0,2,0],[3,0,1]) = [3,2,1]; depois +1 na propria posicao (1) => [3,3,1]
        assertEquals(List.of(3L, 3L, 1L), aposReceber);
    }

    @Test
    void regra3NaoRetrocedeUmaPosicaoOndeOReceptorJaEstaAdiantado() {
        RelogioVetorialService agencia1 = new RelogioVetorialService(1, 3);
        for (int i = 0; i < 5; i++) {
            agencia1.eventoLocal(); // [0,5,0]
        }

        assertEquals(List.of(1L, 6L, 0L), agencia1.aoReceber(List.of(1L, 2L, 0L)));
    }

    @Test
    void sequenciaDeUmaTransferenciaEntreAgenciasProduzOsVetoresEsperados() {
        // O mesmo cenario do roteiro (secao 9.1): a sequencia de vetores e a mesma em qualquer linguagem.
        RelogioVetorialService agencia0 = new RelogioVetorialService(0, 3);
        RelogioVetorialService agencia1 = new RelogioVetorialService(1, 3);

        List<Long> debito = agencia0.eventoLocal();
        List<Long> envio = agencia0.aoEnviar();
        List<Long> credito = agencia1.aoReceber(envio);

        assertEquals(List.of(1L, 0L, 0L), debito);
        assertEquals(List.of(2L, 0L, 0L), envio);
        assertEquals(List.of(2L, 1L, 0L), credito);
        // e a relacao causal fica comprovada pelos vetores, nao so suposta:
        assertEquals(RelacaoCausal.ANTES, Vetores.comparar(debito, credito));
        assertEquals(RelacaoCausal.ANTES, Vetores.comparar(envio, credito));
    }

    @Test
    void vetorDevolvidoNaoMudaDepoisQueORelogioAvanca() {
        RelogioVetorialService agencia0 = new RelogioVetorialService(0, 3);
        List<Long> primeiro = agencia0.eventoLocal();

        agencia0.eventoLocal();

        assertEquals(List.of(1L, 0L, 0L), primeiro, "o vetor gravado em um evento nao pode ser alterado depois");
        assertThrows(UnsupportedOperationException.class, () -> primeiro.set(0, 99L));
    }

    @Test
    void valorAtualNaoContaComoEvento() {
        RelogioVetorialService agencia2 = new RelogioVetorialService(2, 3);
        agencia2.valorAtual();
        agencia2.valorAtual();

        assertEquals(List.of(0L, 0L, 1L), agencia2.eventoLocal());
    }

    @Test
    void aoReceberRejeitaVetorDeTamanhoErrado() {
        RelogioVetorialService agencia0 = new RelogioVetorialService(0, 3);

        assertThrows(IllegalArgumentException.class, () -> agencia0.aoReceber(List.of(1L, 2L)));
        assertThrows(IllegalArgumentException.class, () -> agencia0.aoReceber(null));
        assertEquals(List.of(0L, 0L, 0L), agencia0.valorAtual(), "vetor invalido nao pode alterar o estado");
    }

    @Test
    void construtorRejeitaAgenciaForaDoVetor() {
        assertThrows(IllegalArgumentException.class, () -> new RelogioVetorialService(3, 3));
        assertThrows(IllegalArgumentException.class, () -> new RelogioVetorialService(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> new RelogioVetorialService(0, 0));
    }

    @Test
    void eSeguroSobConcorrenciaEntreThreadsHttpEConsumidor() throws InterruptedException {
        RelogioVetorialService agencia1 = new RelogioVetorialService(1, 3);
        int threads = 8;
        int operacoesPorThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            boolean receber = t % 2 == 0; // metade simula requisicoes HTTP, metade o consumidor de mensagens
            pool.submit(() -> {
                largada.await();
                for (int i = 0; i < operacoesPorThread; i++) {
                    if (receber) {
                        agencia1.aoReceber(List.of(0L, 0L, 0L));
                    } else {
                        agencia1.eventoLocal();
                    }
                }
                return null;
            });
        }
        largada.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        // nenhum incremento pode ter se perdido: 8 threads x 1000 operacoes, todas incrementam a propria posicao
        assertEquals(8_000L, agencia1.valorAtual().get(1));
    }
}
