/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 (correcao apos revisao de codigo)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.common.exceptions;

/**
 * Lancada quando um valor monetario informado (deposito, saque, transferencia)
 * nao e positivo. Sem essa validacao, um valor negativo inverte o efeito da
 * operacao (ex.: "sacar -1000" na verdade credita a conta).
 */
public class ValorInvalidoException extends RuntimeException {
    public ValorInvalidoException(long valor) {
        super("Valor invalido: " + valor + ". O valor precisa ser maior que zero.");
    }
}
