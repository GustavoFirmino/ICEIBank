/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 3 - Parte E (sincronizacao offline-first)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.common.exceptions;

import br.pucminas.labdamd.iceibank.agencia.conta.dto.ContaComVetorResponse;

import java.util.List;

/** O vetor da agencia avancou desde a ultima leitura do app: a operacao pendente nao e aplicada (HTTP 409). */
public class ConflitoSincronizacaoException extends RuntimeException {

    private final List<Long> vetorConhecido;
    private final List<Long> vetorAtual;
    private final ContaComVetorResponse contaAtual;

    public ConflitoSincronizacaoException(List<Long> vetorConhecido, List<Long> vetorAtual, ContaComVetorResponse contaAtual) {
        super("Conflito detectado: o estado da agencia mudou desde a ultima sincronizacao do app.");
        this.vetorConhecido = vetorConhecido;
        this.vetorAtual = vetorAtual;
        this.contaAtual = contaAtual;
    }

    public List<Long> vetorConhecido() {
        return vetorConhecido;
    }

    public List<Long> vetorAtual() {
        return vetorAtual;
    }

    public ContaComVetorResponse contaAtual() {
        return contaAtual;
    }
}
