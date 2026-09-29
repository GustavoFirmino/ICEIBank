/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte E / Sprint 2 - Parte D (Linha do tempo causal)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 *
 * Le os arquivos data/agencia-*.jsonl gerados pelas 3 agencias e monta a linha do
 * tempo unificada. No Sprint 1 ela era ordenada por relogio de Lamport, que so da
 * ordem PARCIAL confiavel numa direcao. No Sprint 2 cada evento carrega um VETOR, e
 * comparar dois vetores diz com certeza se os eventos sao causalmente relacionados
 * (um ANTES do outro) ou CONCORRENTES. O relatorio tem tres partes:
 *
 *   1. a linha do tempo (ordenada por hora de parede - so para exibicao: o relogio
 *      fisico NAO decide nada; a relacao entre eventos vem dos vetores);
 *   2. os pares de eventos de agencias DIFERENTES comprovadamente concorrentes;
 *   3. os pares causais envio -> recebimento, ligados pelo idMensagem, cada um
 *      verificado pela comparacao dos vetores (e que, por isso, NAO aparecem na lista 2).
 *
 * Como executar (a raiz do repositorio):   .\linha-do-tempo.ps1
 */
package br.pucminas.labdamd.iceibank.agencia.ferramentas;

import br.pucminas.labdamd.iceibank.agencia.clock.RelacaoCausal;
import br.pucminas.labdamd.iceibank.agencia.clock.Vetores;
import br.pucminas.labdamd.iceibank.agencia.eventlog.Evento;
import br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.util.Objects;

public class MesclarLogs {

    /** Um par de eventos e a relacao entre eles (do ponto de vista do primeiro). */
    public record ParDeEventos(Evento primeiro, Evento segundo, RelacaoCausal relacao) {
    }

    // Quantos pares concorrentes imprimir antes de resumir (a lista cresce com o quadrado dos eventos).
    private static final int LIMITE_DE_PARES_IMPRESSOS = 40;

    public static void main(String[] args) throws IOException {
        Path pastaDados = Paths.get("data");
        ObjectMapper objectMapper = criarObjectMapper();

        List<Evento> todosEventos = lerTodosOsEventos(pastaDados, objectMapper);
        List<Evento> linhaDoTempo = ordenarPorHoraParede(todosEventos);

        imprimirRelatorio(linhaDoTempo);
    }

    static ObjectMapper criarObjectMapper() {
        // tolerante: um log gerado por versao anterior (Sprint 1, com timestampLamport) nao derruba a analise
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Ordenado por hora de parede, com a agencia como desempate. E so a ORDEM DE EXIBICAO: os
     * relogios fisicos de maquinas diferentes nao sao confiaveis para decidir causalidade -
     * essa decisao e dos vetores ({@link #paresConcorrentes}).
     */
    static List<Evento> ordenarPorHoraParede(List<Evento> eventos) {
        List<Evento> copia = new ArrayList<>(eventos);
        copia.sort(Comparator.comparing(Evento::horaParede).thenComparing(Evento::agencia));
        return copia;
    }

    static List<Evento> lerTodosOsEventos(Path pastaDados, ObjectMapper objectMapper) throws IOException {
        List<Evento> eventos = new ArrayList<>();
        if (!Files.isDirectory(pastaDados)) {
            return eventos;
        }
        int linhasIgnoradas = 0;
        try (DirectoryStream<Path> arquivos = Files.newDirectoryStream(pastaDados, "agencia-*.jsonl")) {
            for (Path arquivo : arquivos) {
                for (String linha : Files.readAllLines(arquivo)) {
                    if (linha.isBlank()) {
                        continue;
                    }
                    try {
                        Evento evento = objectMapper.readValue(linha, Evento.class);
                        if (evento.timestampVetorial() == null) {
                            linhasIgnoradas++; // formato do Sprint 1 (sem vetor)
                            continue;
                        }
                        eventos.add(evento);
                    } catch (IOException erro) {
                        linhasIgnoradas++;
                    }
                }
            }
        }
        if (linhasIgnoradas > 0) {
            System.err.println("(aviso: " + linhasIgnoradas + " linha(s) de log ignorada(s) - formato antigo, sem relogio vetorial)");
        }
        return eventos;
    }

    /**
     * Todos os pares de eventos de agencias DIFERENTES cujos vetores sao concorrentes (nenhum
     * domina o outro). Compara todos os pares: O(n^2) no numero de eventos.
     */
    static List<ParDeEventos> paresConcorrentes(List<Evento> eventos) {
        List<ParDeEventos> pares = new ArrayList<>();
        for (int i = 0; i < eventos.size(); i++) {
            for (int j = i + 1; j < eventos.size(); j++) {
                Evento a = eventos.get(i);
                Evento b = eventos.get(j);
                if (a.agencia().equals(b.agencia())) {
                    continue; // na MESMA agencia os eventos sao sempre totalmente ordenados
                }
                if (Vetores.comparar(a.timestampVetorial(), b.timestampVetorial()) == RelacaoCausal.CONCORRENTES) {
                    pares.add(new ParDeEventos(a, b, RelacaoCausal.CONCORRENTES));
                }
            }
        }
        return pares;
    }

    /**
     * Para cada mensagem, o par (TRANSFERENCIA_PUBLICADA na origem -> evento de recebimento no
     * destino), ligados pelo idMensagem, com a relacao CALCULADA pelos vetores (nao suposta).
     * Esperado: ANTES. Qualquer outra coisa indicaria um bug no relogio.
     */
    static List<ParDeEventos> paresCausaisPorMensagem(List<Evento> eventos) {
        List<ParDeEventos> pares = new ArrayList<>();
        for (Evento envio : eventos) {
            if (envio.tipo() != TipoEvento.TRANSFERENCIA_PUBLICADA) {
                continue;
            }
            Object idMensagem = envio.detalhes().get("idMensagem");
            for (Evento recebimento : eventos) {
                boolean eRecebimento = recebimento.tipo() == TipoEvento.TRANSFERENCIA_CREDITO_REMOTO
                        || recebimento.tipo() == TipoEvento.CREDITO_REMOTO_FALHOU;
                if (eRecebimento && Objects.equals(idMensagem, recebimento.detalhes().get("idMensagem"))) {
                    pares.add(new ParDeEventos(envio, recebimento,
                            Vetores.comparar(envio.timestampVetorial(), recebimento.timestampVetorial())));
                }
            }
        }
        return pares;
    }

    private static void imprimirRelatorio(List<Evento> linhaDoTempo) {
        System.out.println("=== 1. Linha do tempo (ordenada por hora de parede - so exibicao) ===");
        if (linhaDoTempo.isEmpty()) {
            System.out.println("(nenhum evento encontrado em data/agencia-*.jsonl - rode as agencias e gere alguns eventos primeiro)");
            return;
        }
        for (int i = 0; i < linhaDoTempo.size(); i++) {
            Evento e = linhaDoTempo.get(i);
            System.out.printf("#%-2d %-9s vetor=%-10s %s %s%n", i + 1, e.agencia(), e.timestampVetorial(), e.tipo(), resumir(e));
        }

        List<ParDeEventos> concorrentes = paresConcorrentes(linhaDoTempo);
        System.out.println();
        System.out.println("=== 2. Pares CONCORRENTES entre agencias diferentes (nenhum influenciou o outro) ===");
        int impressos = 0;
        for (ParDeEventos par : concorrentes) {
            if (impressos++ == LIMITE_DE_PARES_IMPRESSOS) {
                System.out.println("... e mais " + (concorrentes.size() - LIMITE_DE_PARES_IMPRESSOS) + " pares.");
                break;
            }
            System.out.printf("#%-2d %-9s %-27s %s  ||  #%-2d %-9s %-27s %s%n",
                    numero(linhaDoTempo, par.primeiro()), par.primeiro().agencia(), par.primeiro().tipo(), par.primeiro().timestampVetorial(),
                    numero(linhaDoTempo, par.segundo()), par.segundo().agencia(), par.segundo().tipo(), par.segundo().timestampVetorial());
        }
        if (concorrentes.isEmpty()) {
            System.out.println("(nenhum par concorrente nesta execucao - gere operacoes independentes em agencias diferentes e rode de novo)");
        }

        List<ParDeEventos> causais = paresCausaisPorMensagem(linhaDoTempo);
        System.out.println();
        System.out.println("=== 3. Pares CAUSAIS envio -> recebimento (ligados pelo idMensagem; relacao calculada pelos vetores) ===");
        boolean todosConsistentes = true;
        for (ParDeEventos par : causais) {
            boolean ok = par.relacao() == RelacaoCausal.ANTES;
            todosConsistentes &= ok;
            System.out.printf("#%-2d %-9s %s %s  ->  #%-2d %-9s %s %s   [%s]%n",
                    numero(linhaDoTempo, par.primeiro()), par.primeiro().agencia(), par.primeiro().tipo(), par.primeiro().timestampVetorial(),
                    numero(linhaDoTempo, par.segundo()), par.segundo().agencia(), par.segundo().tipo(), par.segundo().timestampVetorial(),
                    ok ? "ANTES - causal" : "INCONSISTENTE: " + par.relacao());
        }
        if (causais.isEmpty()) {
            System.out.println("(nenhuma transferencia entre agencias com envio E recebimento nos logs)");
        } else {
            System.out.println(todosConsistentes
                    ? "OK: todos os pares envio -> recebimento sao ANTES; nenhum deles esta na lista de concorrentes."
                    : "ATENCAO: ha pares envio -> recebimento que NAO sao ANTES - verifique o relogio vetorial.");
        }

        System.out.println();
        System.out.printf("Resumo: %d eventos, %d pares concorrentes entre agencias diferentes, %d transferencia(s) entre agencias.%n",
                linhaDoTempo.size(), concorrentes.size(), causais.size());
    }

    private static int numero(List<Evento> linhaDoTempo, Evento evento) {
        // identidade, nao equals: dois eventos com o mesmo conteudo continuam sendo eventos diferentes
        for (int i = 0; i < linhaDoTempo.size(); i++) {
            if (linhaDoTempo.get(i) == evento) {
                return i + 1;
            }
        }
        return -1;
    }

    private static String resumir(Evento e) {
        Object idMensagem = e.detalhes().get("idMensagem");
        String base = "conta=" + e.idConta() + " " + e.detalhes().entrySet().stream()
                .filter(entrada -> !entrada.getKey().equals("idMensagem"))
                .map(entrada -> entrada.getKey() + "=" + entrada.getValue())
                .sorted()
                .toList();
        return idMensagem == null ? base : base + " msg=" + String.valueOf(idMensagem).substring(0, 8);
    }
}
