/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 (correcao apos revisao de codigo)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 *
 * Fonte unica de verdade para as rotas que NAO exigem JWT de usuario -
 * usada tanto por SecurityConfig (authorizeHttpRequests) quanto por
 * JwtAuthFilter (shouldNotFilter). Antes da correcao, essa lista existia
 * duplicada e independente nas duas classes, com risco de ficarem
 * dessincronizadas (uma rota liberada em uma config e nao na outra).
 */
package br.pucminas.labdamd.iceibank.agencia.config;

import java.util.List;

public final class SecurityPaths {

    public static final String LOGIN = "/auth/login";
    public static final String CREDITAR_REMOTO = "/contas/*/creditar-remoto";
    public static final String DESIGN_SYSTEM = "/design-system";

    public static final List<String> PUBLICAS = List.of(LOGIN, CREDITAR_REMOTO, DESIGN_SYSTEM);

    private SecurityPaths() {
    }
}
