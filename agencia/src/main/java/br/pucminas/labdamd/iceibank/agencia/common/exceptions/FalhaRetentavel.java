/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte C (Publish/Subscribe entre agencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.common.exceptions;

/**
 * Marca uma falha depois da qual o estado foi totalmente desfeito, entao
 * REENVIAR a mesma operacao e seguro. O IdempotencyStore NAO guarda falhas
 * com esta marca: se guardasse, uma queda momentanea do broker faria o mesmo
 * idOperacao falhar para sempre, mesmo depois do broker voltar.
 */
public interface FalhaRetentavel {
}
