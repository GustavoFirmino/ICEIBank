/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte C (API REST/MVC de contas)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.common.exceptions;

public class ParticaoInvalidaException extends RuntimeException {
    public ParticaoInvalidaException(long idConta, int agenciaResponsavel) {
        super("Conta " + idConta + " nao pertence a esta agencia (pertence a agencia " + agenciaResponsavel + ").");
    }
}
