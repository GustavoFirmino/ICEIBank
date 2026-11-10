/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 3 - Parte E (sincronizacao offline-first)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.conta.dto;

import java.util.List;

/** Corpo de POST /contas/{id}/sincronizar: {"tipo":"DEPOSITO","valor":50,"vetorConhecido":[3,1,0]}. */
public record SincronizarRequest(String tipo, long valor, List<Long> vetorConhecido) {
}
