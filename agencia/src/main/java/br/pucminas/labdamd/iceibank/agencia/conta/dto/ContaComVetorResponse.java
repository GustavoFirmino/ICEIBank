/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 3 - Parte E (sincronizacao offline-first)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.conta.dto;

import br.pucminas.labdamd.iceibank.agencia.conta.Conta;

import java.util.List;

/** Conta + vetor atual da agencia: o app guarda o vetor e o devolve ao sincronizar operacoes feitas offline. */
public record ContaComVetorResponse(long id, String titular, long saldo, List<Long> vetorAtual) {
    public static ContaComVetorResponse de(Conta conta, List<Long> vetorAtual) {
        return new ContaComVetorResponse(conta.id(), conta.titular(), conta.saldo(), vetorAtual);
    }
}
