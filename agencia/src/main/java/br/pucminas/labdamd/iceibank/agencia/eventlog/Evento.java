/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte B (Relogio de Lamport e registro de eventos)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.eventlog;

import java.time.Instant;
import java.util.Map;

/**
 * Um evento registrado por uma agencia. Guarda dois carimbos de tempo:
 * timestampLamport (o relogio logico, usado para ordenar causalmente) e
 * horaParede (o relogio fisico da maquina, so para comparacao - nao e usado
 * para nenhuma decisao do sistema).
 *
 * idConta fica como campo de primeira classe (nao soterrado dentro de
 * "detalhes") para que o historico por conta (funcionalidade adicional)
 * possa filtrar sem precisar inspecionar o mapa de detalhes.
 */
public record Evento(
        String agencia,
        TipoEvento tipo,
        long timestampLamport,
        Instant horaParede,
        Long idConta,
        Map<String, Object> detalhes
) {
}
