/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 (correcao apos revisao de codigo)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.common.exceptions;

/**
 * Lancada por uma implementacao de RemoteBranchClient quando a comunicacao
 * com outra agencia falha (rede, timeout, resposta de erro). E um tipo
 * proprio (em vez de deixar TransferenciaService capturar RuntimeException
 * generico) para nao mascarar bugs nao relacionados a rede como se fossem
 * "agencia de destino indisponivel".
 */
public class ComunicacaoAgenciaException extends RuntimeException {
    public ComunicacaoAgenciaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
