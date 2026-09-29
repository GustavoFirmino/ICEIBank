/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte B/D (comparacao de vetores)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.clock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VetoresTest {

    @Test
    void pergunta642_v1MenorOuIgualEmTodaPosicaoEntaoV1AconteceuAntes() {
        // V1 = [3,1,0], V2 = [3,2,0]: 3<=3, 1<=2, 0<=0 e sao diferentes
        assertEquals(RelacaoCausal.ANTES, Vetores.comparar(List.of(3L, 1L, 0L), List.of(3L, 2L, 0L)));
    }

    @Test
    void pergunta643_nenhumDominaOOutroEntaoConcorrentes() {
        // V1 = [3,1,0], V2 = [1,3,0]: posicao 0 favorece V1 (3>1), posicao 1 favorece V2 (3>1)
        assertEquals(RelacaoCausal.CONCORRENTES, Vetores.comparar(List.of(3L, 1L, 0L), List.of(1L, 3L, 0L)));
    }

    @Test
    void ordemInvertidaDaDEPOIS() {
        assertEquals(RelacaoCausal.DEPOIS, Vetores.comparar(List.of(3L, 2L, 0L), List.of(3L, 1L, 0L)));
    }

    @Test
    void vetoresIdenticosSaoIguais() {
        assertEquals(RelacaoCausal.IGUAIS, Vetores.comparar(List.of(2L, 1L, 0L), List.of(2L, 1L, 0L)));
    }

    @Test
    void primeirosEventosDeAgenciasQueNuncaSeFalaramSaoConcorrentes() {
        // O par que o Lamport do Sprint 1 NAO conseguia classificar (ambos com timestamp 1): agora e provado.
        assertEquals(RelacaoCausal.CONCORRENTES, Vetores.comparar(List.of(1L, 0L, 0L), List.of(0L, 1L, 0L)));
    }

    @Test
    void relacaoEAntissimetrica() {
        List<Long> a = List.of(1L, 0L, 2L);
        List<Long> b = List.of(2L, 1L, 2L);
        assertEquals(RelacaoCausal.ANTES, Vetores.comparar(a, b));
        assertEquals(RelacaoCausal.DEPOIS, Vetores.comparar(b, a));
    }

    @Test
    void tamanhosDiferentesSaoRejeitados() {
        assertThrows(IllegalArgumentException.class, () -> Vetores.comparar(List.of(1L, 2L), List.of(1L, 2L, 3L)));
        assertThrows(IllegalArgumentException.class, () -> Vetores.comparar(null, List.of(1L)));
    }
}
