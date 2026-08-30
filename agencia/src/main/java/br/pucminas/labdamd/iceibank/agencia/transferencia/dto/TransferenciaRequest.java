/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia.dto;

/**
 * idOperacao e OPCIONAL: um identificador unico (ex.: UUID) gerado pelo
 * CLIENTE para esta operacao especifica. Quando informado, reenviar a
 * mesma requisicao (mesmo idOperacao) nao aplica a transferencia de novo -
 * ver funcionalidade adicional "idempotencia" em RESPOSTAS.md. Se omitido,
 * a transferencia funciona exatamente como no escopo base do roteiro
 * (sem protecao contra reenvio).
 */
public record TransferenciaRequest(long idOrigem, long idDestino, long valor, String idOperacao) {

    /** Construtor de conveniencia sem idempotencia (idOperacao = null). */
    public TransferenciaRequest(long idOrigem, long idDestino, long valor) {
        this(idOrigem, idDestino, valor, null);
    }
}
