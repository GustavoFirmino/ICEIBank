/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.common.exceptions;

/**
 * LIMITACAO CONHECIDA: lancada quando a chamada REST para a agencia de
 * destino falha durante uma transferencia entre agencias. O debito ja
 * aplicado na agencia de origem NAO e revertido - o dinheiro "desaparece"
 * temporariamente. Resolver isso de verdade (garantir atomicidade mesmo sob
 * falha) e o assunto do Sprint 4, com uma transacao distribuida (2PC/Saga).
 * Por enquanto, a inconsistencia e apenas registrada no log de eventos.
 */
public class AgenciaDestinoIndisponivelException extends RuntimeException {
    public AgenciaDestinoIndisponivelException(String message) {
        super(message);
    }
}
