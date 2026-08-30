/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias) + funcionalidade adicional (idempotencia)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia.dto;

/**
 * "repetida" e true quando a resposta veio do cache de idempotencia (a
 * transferencia NAO foi aplicada de novo - foi so devolvida a mesma
 * resposta da primeira vez que este idOperacao foi processado).
 */
public record TransferenciaResponse(String mensagem, boolean repetida) {

    public static TransferenciaResponse nova(String mensagem) {
        return new TransferenciaResponse(mensagem, false);
    }
}
