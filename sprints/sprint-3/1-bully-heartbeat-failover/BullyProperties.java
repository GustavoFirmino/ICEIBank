/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 3 - Parte A/B (Algoritmo de Bully) + funcionalidade adicional (timeouts configuraveis)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parametros do Bully, configuraveis por variavel de ambiente (BULLY_HEARTBEAT_MS, BULLY_TIMEOUT_MS,
 * BULLY_HABILITADO) ou em application.yml (prefixo "bully").
 *
 * @param habilitado  false desliga eleicao e heartbeat (usado em testes)
 * @param heartbeatMs de quanto em quanto tempo a agencia checa se a lider ainda responde
 * @param timeoutMs   quanto esperar uma resposta de outra agencia antes de considera-la fora do ar
 */
@ConfigurationProperties(prefix = "bully")
public record BullyProperties(
        @DefaultValue("true") boolean habilitado,
        @DefaultValue("3000") long heartbeatMs,
        @DefaultValue("1000") long timeoutMs) {
}
