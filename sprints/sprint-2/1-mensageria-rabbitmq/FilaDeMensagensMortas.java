/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Funcionalidade adicional (dead-letter queue com reprocessamento)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.mensageria;

/**
 * Acesso a dead-letter queue (fila-creditos-mortos) para INSPECAO e REPROCESSAMENTO.
 * E uma interface para que a logica de MensagensMortasService seja testavel sem broker.
 */
public interface FilaDeMensagensMortas {

    /** O que fazer com a mensagem que o visitante acabou de examinar. */
    enum Decisao {
        /** Remove a mensagem da dead-letter queue (ja foi tratada). */
        CONFIRMAR,
        /** Deixa a mensagem na fila, intacta, para uma proxima vez. */
        DEVOLVER
    }

    interface Visitante {
        Decisao visitar(CreditoRemotoMensagem mensagem);
    }

    /**
     * Examina ate {@code max} mensagens da fila, uma por vez. Uma mensagem so sai da fila se o
     * visitante devolver {@link Decisao#CONFIRMAR}; qualquer outra coisa (inclusive uma excecao)
     * a deixa onde estava.
     */
    void percorrer(int max, Visitante visitante);
}
