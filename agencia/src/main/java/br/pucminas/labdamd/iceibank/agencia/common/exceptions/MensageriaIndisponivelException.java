/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte C (Publish/Subscribe entre agencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.common.exceptions;

/**
 * O broker (RabbitMQ) nao aceitou a mensagem: fora do ar, conexao recusada ou
 * sem confirmacao dentro do prazo. Como NADA foi publicado, o debito local ja
 * aplicado e estornado antes desta excecao subir - por isso e uma falha
 * retentavel (o cliente pode reenviar). Vira HTTP 503.
 *
 * Diferenca para o Sprint 1: la, a falha era "a AGENCIA de destino esta fora
 * do ar" e o debito ficava pendurado. Agora uma agencia de destino fora do ar
 * NAO e erro (a mensagem fica retida na fila); so o broker fora do ar e.
 */
public class MensageriaIndisponivelException extends RuntimeException implements FalhaRetentavel {
    public MensageriaIndisponivelException(String message, Throwable causa) {
        super(message, causa);
    }
}
