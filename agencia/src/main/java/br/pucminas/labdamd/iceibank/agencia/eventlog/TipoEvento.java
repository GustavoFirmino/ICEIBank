/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte B (Relogio de Lamport e registro de eventos)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.eventlog;

public enum TipoEvento {
    CRIACAO_CONTA,
    DEPOSITO,
    SAQUE,
    TRANSFERENCIA_DEBITO,
    TRANSFERENCIA_CREDITO,
    TRANSFERENCIA_CREDITO_REMOTO,
    TRANSFERENCIA_FALHOU,
    TRANSFERENCIA_REVERTIDA,
    LOGIN
}
