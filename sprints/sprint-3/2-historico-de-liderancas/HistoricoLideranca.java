/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 3 - Parte C (Observando a eleicao)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 *
 * Le os data/agencia-*.jsonl das 3 agencias, filtra os eventos do algoritmo de Bully e imprime
 * (1) todos os eventos de eleicao em ordem de hora de parede e (2) a linha do tempo de lideres
 * (cada TORNOU_LIDER), alem de um resumo de quantas vezes a lideranca mudou de mao.
 *
 * Como executar (a raiz do repositorio):   .\historico-lideranca.ps1
 */
package br.pucminas.labdamd.iceibank.agencia.ferramentas;

import br.pucminas.labdamd.iceibank.agencia.eventlog.Evento;
import br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class HistoricoLideranca {

    static final Set<TipoEvento> TIPOS_BULLY = EnumSet.of(
            TipoEvento.ELEICAO_INICIADA, TipoEvento.ELEICAO_RECEBIDA, TipoEvento.TORNOU_LIDER,
            TipoEvento.LIDER_ANUNCIADO, TipoEvento.LIDER_NAO_RESPONDEU);

    public static void main(String[] args) throws IOException {
        Path pastaDados = Paths.get("data");
        ObjectMapper objectMapper = MesclarLogs.criarObjectMapper();
        List<Evento> todos = MesclarLogs.lerTodosOsEventos(pastaDados, objectMapper);
        List<Evento> bully = filtrarEventosBully(todos);

        System.out.println("=== Historico de eventos de eleicao ===");
        for (Evento e : bully) {
            System.out.println("(" + e.horaParede() + ") [" + e.agencia() + "] " + e.tipo() + " " + e.detalhes());
        }

        List<Evento> lideres = bully.stream().filter(e -> e.tipo() == TipoEvento.TORNOU_LIDER).toList();
        System.out.println("\n=== Linha do tempo de lideres ===");
        for (Evento e : lideres) {
            System.out.println("A partir de " + e.horaParede() + ": Agencia " + e.detalhes().get("idAgencia") + " e a lider");
        }
        System.out.println("\nA lideranca foi assumida " + lideres.size() + " vez(es) (" + trocas(lideres)
                + " troca(s) de lider entre agencias diferentes).");
    }

    /** Somente eventos do Bully, ordenados por hora de parede (so exibicao - os vetores decidem causalidade). */
    static List<Evento> filtrarEventosBully(List<Evento> eventos) {
        return MesclarLogs.ordenarPorHoraParede(
                eventos.stream().filter(e -> TIPOS_BULLY.contains(e.tipo())).toList());
    }

    /** Quantas vezes duas assuncoes consecutivas de lideranca foram de agencias diferentes. */
    static int trocas(List<Evento> tornouLider) {
        int trocas = 0;
        Object anterior = null;
        for (Evento e : tornouLider) {
            Object atual = e.detalhes().get("idAgencia");
            if (anterior != null && !anterior.equals(atual)) {
                trocas++;
            }
            anterior = atual;
        }
        return trocas;
    }
}
