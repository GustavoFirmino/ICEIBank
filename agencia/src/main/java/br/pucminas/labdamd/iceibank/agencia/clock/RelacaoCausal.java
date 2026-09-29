/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte B/D (Relogio vetorial e linha do tempo causal)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.clock;

/** Relacao entre dois eventos, decidida pela comparacao dos seus vetores. */
public enum RelacaoCausal {
    /** Vetores identicos (o mesmo ponto no tempo logico). */
    IGUAIS,
    /** O primeiro evento aconteceu ANTES do segundo (V1 <= V2, V1 != V2). */
    ANTES,
    /** O primeiro evento aconteceu DEPOIS do segundo (V2 <= V1, V1 != V2). */
    DEPOIS,
    /** Nenhum influenciou o outro: nem V1 <= V2 nem V2 <= V1. */
    CONCORRENTES
}
