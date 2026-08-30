/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Usuarios de demonstracao (login de aplicativo, sem vinculo com uma conta
 * bancaria especifica - ver justificativa na Parte F do RESPOSTAS.md).
 * Precisam ser identicos nas 3 agencias, por isso ficam no application.yml
 * compartilhado, nunca nos arquivos application-agenciaN.yml.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(List<UsuarioDemo> usuarios) {

    public record UsuarioDemo(String username, String password) {
    }
}
