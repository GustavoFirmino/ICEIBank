/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.auth;

/**
 * Usuario de aplicativo (login), sem vinculo 1:1 com uma conta bancaria -
 * ver justificativa desse modelo na Parte F do RESPOSTAS.md.
 */
public record Usuario(String username, String passwordHash) {
}
