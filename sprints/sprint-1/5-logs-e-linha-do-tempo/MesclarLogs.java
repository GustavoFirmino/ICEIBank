/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte E (Linha do tempo unificada)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 *
 * Le os arquivos data/agencia-*.jsonl gerados pelas 3 agencias e monta uma
 * unica linha do tempo, ordenada por relogio de Lamport - para observar, na
 * pratica, o algoritmo funcionando entre processos diferentes.
 *
 * Como executar (a partir da pasta agencia/):
 *   mvn -q compile exec:java "-Dexec.mainClass=br.pucminas.labdamd.iceibank.agencia.ferramentas.MesclarLogs"
 */
package br.pucminas.labdamd.iceibank.agencia.ferramentas;

import br.pucminas.labdamd.iceibank.agencia.eventlog.Evento;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MesclarLogs {

    public static void main(String[] args) throws IOException {
        Path pastaDados = Paths.get("data");
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        List<Evento> todosEventos = lerTodosOsEventos(pastaDados, objectMapper);
        List<Evento> ordenados = ordenarPorLamport(todosEventos);

        imprimirLinhaDoTempo(ordenados);
    }

    /**
     * Ordenado principalmente por relogio de Lamport - horaParede e agencia so
     * servem de criterio de desempate quando o timestamp logico empata (o
     * que, como o relogio de Lamport NAO garante ordem total entre eventos
     * concorrentes, pode acontecer de verdade - ver Parte E do RESPOSTAS.md).
     */
    static List<Evento> ordenarPorLamport(List<Evento> eventos) {
        List<Evento> copia = new ArrayList<>(eventos);
        copia.sort(
                Comparator.comparingLong(Evento::timestampLamport)
                        .thenComparing(Evento::horaParede)
                        .thenComparing(Evento::agencia));
        return copia;
    }

    static List<Evento> lerTodosOsEventos(Path pastaDados, ObjectMapper objectMapper) throws IOException {
        List<Evento> eventos = new ArrayList<>();
        if (!Files.isDirectory(pastaDados)) {
            return eventos;
        }
        try (DirectoryStream<Path> arquivos = Files.newDirectoryStream(pastaDados, "agencia-*.jsonl")) {
            for (Path arquivo : arquivos) {
                for (String linha : Files.readAllLines(arquivo)) {
                    if (linha.isBlank()) {
                        continue;
                    }
                    eventos.add(objectMapper.readValue(linha, Evento.class));
                }
            }
        }
        return eventos;
    }

    private static void imprimirLinhaDoTempo(List<Evento> eventos) {
        System.out.println("=== Linha do tempo unificada (ordenada por relogio de Lamport) ===");
        if (eventos.isEmpty()) {
            System.out.println("(nenhum evento encontrado em data/agencia-*.jsonl - rode as agencias e gere alguns eventos primeiro)");
            return;
        }
        for (Evento evento : eventos) {
            System.out.printf(
                    "[Lamport %d] (%s) %s - %s %s%n",
                    evento.timestampLamport(), evento.horaParede(), evento.agencia(), evento.tipo(), evento.detalhes());
        }
    }
}
