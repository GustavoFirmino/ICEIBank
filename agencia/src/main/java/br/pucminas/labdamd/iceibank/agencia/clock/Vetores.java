/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte B/D (Relogio vetorial e linha do tempo causal)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.clock;

import java.util.List;

public final class Vetores {

    private Vetores() {
    }

    /**
     * Compara posicao a posicao:
     *  - v1[i] <= v2[i] para todo i (e sao diferentes)  => ANTES
     *  - v2[i] <= v1[i] para todo i (e sao diferentes)  => DEPOIS
     *  - iguais em todas as posicoes                    => IGUAIS
     *  - caso contrario (cada um "na frente" em alguma posicao) => CONCORRENTES
     */
    public static RelacaoCausal comparar(List<Long> v1, List<Long> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size()) {
            throw new IllegalArgumentException("Vetores precisam ter o mesmo tamanho: " + v1 + " x " + v2);
        }
        boolean v1MenorOuIgual = true;
        boolean v2MenorOuIgual = true;
        for (int i = 0; i < v1.size(); i++) {
            if (v1.get(i) > v2.get(i)) {
                v1MenorOuIgual = false;
            }
            if (v2.get(i) > v1.get(i)) {
                v2MenorOuIgual = false;
            }
        }
        if (v1MenorOuIgual && v2MenorOuIgual) {
            return RelacaoCausal.IGUAIS;
        }
        if (v1MenorOuIgual) {
            return RelacaoCausal.ANTES;
        }
        if (v2MenorOuIgual) {
            return RelacaoCausal.DEPOIS;
        }
        return RelacaoCausal.CONCORRENTES;
    }
}
