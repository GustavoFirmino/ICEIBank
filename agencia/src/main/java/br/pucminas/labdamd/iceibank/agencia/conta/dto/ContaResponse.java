/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte C (API REST/MVC de contas)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.conta.dto;

import br.pucminas.labdamd.iceibank.agencia.conta.Conta;

public record ContaResponse(long id, String titular, long saldo) {
    public static ContaResponse de(Conta conta) {
        return new ContaResponse(conta.id(), conta.titular(), conta.saldo());
    }
}
