/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte C e funcionalidade adicional (dead-letter)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.common.exceptions;

/**
 * O consumidor recebeu um credito remoto que esta agencia NAO consegue aplicar
 * (ex.: a conta de destino nao existe aqui - o caso da agencia que reiniciou e
 * perdeu as contas em memoria). E lancada para que a mensagem seja REJEITADA
 * e va para a dead-letter queue, em vez de ser confirmada (e o dinheiro sumir
 * em silencio) ou reentregue em loop.
 */
public class CreditoRemotoNaoAplicavelException extends RuntimeException {
    public CreditoRemotoNaoAplicavelException(String message) {
        super(message);
    }
}
