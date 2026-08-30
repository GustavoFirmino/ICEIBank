/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Funcionalidade adicional (historico de transacoes por conta)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.conta.dto;

import br.pucminas.labdamd.iceibank.agencia.eventlog.Evento;

import java.util.Map;

public record HistoricoEventoResponse(String tipo, long timestampLamport, String horaParede, Map<String, Object> detalhes) {

    public static HistoricoEventoResponse de(Evento evento) {
        return new HistoricoEventoResponse(
                evento.tipo().name(), evento.timestampLamport(), evento.horaParede().toString(), evento.detalhes());
    }
}
