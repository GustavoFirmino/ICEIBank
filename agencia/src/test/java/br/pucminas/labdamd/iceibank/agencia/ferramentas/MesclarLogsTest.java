/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte E (Linha do tempo unificada)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.ferramentas;

import br.pucminas.labdamd.iceibank.agencia.clock.RelacaoCausal;
import br.pucminas.labdamd.iceibank.agencia.eventlog.Evento;
import br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MesclarLogsTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private Path pastaDeTeste;

    @AfterEach
    void limpar() throws IOException {
        if (pastaDeTeste != null && Files.exists(pastaDeTeste)) {
            try (var arquivos = Files.walk(pastaDeTeste)) {
                arquivos.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void lerTodosOsEventosJuntaLinhasDeVariosArquivos() throws IOException {
        pastaDeTeste = Files.createTempDirectory("mesclar-logs-teste");
        escreverLinha(pastaDeTeste.resolve("agencia-0.jsonl"), evento("agencia-0", TipoEvento.CRIACAO_CONTA, 1));
        escreverLinha(pastaDeTeste.resolve("agencia-1.jsonl"), evento("agencia-1", TipoEvento.CRIACAO_CONTA, 1));
        escreverLinha(pastaDeTeste.resolve("agencia-1.jsonl"), evento("agencia-1", TipoEvento.DEPOSITO, 2));

        List<Evento> eventos = MesclarLogs.lerTodosOsEventos(pastaDeTeste, objectMapper);

        assertEquals(3, eventos.size());
    }

    @Test
    void ordenarPorHoraParedeUsaORelogioFisicoSoParaExibicao() {
        Evento tarde = eventoEm("agencia-0", "2026-01-01T10:00:03Z");
        Evento cedo = eventoEm("agencia-1", "2026-01-01T10:00:01Z");
        Evento meio = eventoEm("agencia-2", "2026-01-01T10:00:02Z");

        List<Evento> ordenados = MesclarLogs.ordenarPorHoraParede(List.of(tarde, cedo, meio));

        assertEquals("agencia-1", ordenados.get(0).agencia());
        assertEquals("agencia-2", ordenados.get(1).agencia());
        assertEquals("agencia-0", ordenados.get(2).agencia());
    }

    @Test
    void ordenarPorHoraParedeDesempataPorAgenciaQuandoAHoraEIgual() {
        Evento daAgencia2 = new Evento("agencia-2", TipoEvento.CRIACAO_CONTA, List.of(0L, 0L, 1L), Instant.parse("2026-01-01T10:00:00Z"), 2L, Map.of());
        Evento daAgencia0 = new Evento("agencia-0", TipoEvento.CRIACAO_CONTA, List.of(1L, 0L, 0L), Instant.parse("2026-01-01T10:00:00Z"), 0L, Map.of());

        List<Evento> ordenados = MesclarLogs.ordenarPorHoraParede(List.of(daAgencia2, daAgencia0));

        assertEquals("agencia-0", ordenados.get(0).agencia());
        assertEquals("agencia-2", ordenados.get(1).agencia());
    }

    // ------------------------------------------------------------------ Sprint 2: analise causal por vetores

    /** O cenario do roteiro: uma conta em cada agencia (sem se falar) e depois uma transferencia 0 -> 1. */
    private List<Evento> cenarioComTransferencia() {
        Map<String, Object> msg = Map.of("idMensagem", "m-0001", "valor", 30);
        return List.of(
                eventoVetor("agencia-0", TipoEvento.CRIACAO_CONTA, List.of(1L, 0L, 0L), Map.of()),
                eventoVetor("agencia-1", TipoEvento.CRIACAO_CONTA, List.of(0L, 1L, 0L), Map.of()),
                eventoVetor("agencia-0", TipoEvento.TRANSFERENCIA_DEBITO, List.of(2L, 0L, 0L), Map.of("valor", 30)),
                eventoVetor("agencia-0", TipoEvento.TRANSFERENCIA_PUBLICADA, List.of(3L, 0L, 0L), msg),
                eventoVetor("agencia-1", TipoEvento.TRANSFERENCIA_CREDITO_REMOTO, List.of(3L, 2L, 0L), msg));
    }

    @Test
    void criacoesDeContaEmAgenciasDiferentesSemTransferenciaSaoConcorrentes() {
        List<Evento> eventos = cenarioComTransferencia();

        List<MesclarLogs.ParDeEventos> concorrentes = MesclarLogs.paresConcorrentes(eventos);

        boolean achouOParDeCriacoes = concorrentes.stream().anyMatch(p ->
                p.primeiro().tipo() == TipoEvento.CRIACAO_CONTA && p.segundo().tipo() == TipoEvento.CRIACAO_CONTA);
        assertTrue(achouOParDeCriacoes, "[1,0,0] x [0,1,0]: nenhum domina o outro");
    }

    @Test
    void oParDebitoCreditoDeUmaTransferenciaNaoApareceComoConcorrente() {
        List<Evento> eventos = cenarioComTransferencia();

        List<MesclarLogs.ParDeEventos> concorrentes = MesclarLogs.paresConcorrentes(eventos);

        boolean debitoXCredito = concorrentes.stream().anyMatch(p ->
                (p.primeiro().tipo() == TipoEvento.TRANSFERENCIA_DEBITO && p.segundo().tipo() == TipoEvento.TRANSFERENCIA_CREDITO_REMOTO)
                        || (p.primeiro().tipo() == TipoEvento.TRANSFERENCIA_CREDITO_REMOTO && p.segundo().tipo() == TipoEvento.TRANSFERENCIA_DEBITO));
        assertFalse(debitoXCredito, "o debito [2,0,0] aconteceu ANTES do credito [3,2,0]: relacao causal, nao concorrencia");
        boolean envioXRecebimento = concorrentes.stream().anyMatch(p ->
                p.primeiro().tipo() == TipoEvento.TRANSFERENCIA_PUBLICADA && p.segundo().tipo() == TipoEvento.TRANSFERENCIA_CREDITO_REMOTO);
        assertFalse(envioXRecebimento);
    }

    @Test
    void eventosDaMesmaAgenciaNuncaFormamParesConcorrentes() {
        List<Evento> eventos = cenarioComTransferencia();

        assertTrue(MesclarLogs.paresConcorrentes(eventos).stream()
                .noneMatch(p -> p.primeiro().agencia().equals(p.segundo().agencia())));
    }

    @Test
    void oEventoLocalDeOutraAgenciaEConcorrenteComOsEventosDaTransferenciaAntesDelaChegar() {
        List<Evento> eventos = cenarioComTransferencia();

        List<MesclarLogs.ParDeEventos> concorrentes = MesclarLogs.paresConcorrentes(eventos);

        // criar conta na agencia 1 [0,1,0] nao tem relacao com o debito [2,0,0] na agencia 0
        assertTrue(concorrentes.stream().anyMatch(p ->
                (p.primeiro().tipo() == TipoEvento.TRANSFERENCIA_DEBITO && p.segundo().agencia().equals("agencia-1")
                        && p.segundo().tipo() == TipoEvento.CRIACAO_CONTA)
                        || (p.segundo().tipo() == TipoEvento.TRANSFERENCIA_DEBITO && p.primeiro().agencia().equals("agencia-1")
                        && p.primeiro().tipo() == TipoEvento.CRIACAO_CONTA)));
    }

    @Test
    void paresCausaisLigamEnvioERecebimentoPeloIdMensagemComRelacaoCalculadaPelosVetores() {
        List<MesclarLogs.ParDeEventos> causais = MesclarLogs.paresCausaisPorMensagem(cenarioComTransferencia());

        assertEquals(1, causais.size());
        assertEquals(TipoEvento.TRANSFERENCIA_PUBLICADA, causais.get(0).primeiro().tipo());
        assertEquals(TipoEvento.TRANSFERENCIA_CREDITO_REMOTO, causais.get(0).segundo().tipo());
        assertEquals(RelacaoCausal.ANTES, causais.get(0).relacao());
    }

    @Test
    void recebimentoQueFalhouPorContaInexistenteTambemEUmParCausalComOEnvio() {
        Map<String, Object> msg = Map.of("idMensagem", "m-9999");
        List<Evento> eventos = List.of(
                eventoVetor("agencia-0", TipoEvento.TRANSFERENCIA_PUBLICADA, List.of(6L, 0L, 0L), msg),
                eventoVetor("agencia-1", TipoEvento.CREDITO_REMOTO_FALHOU, List.of(6L, 1L, 0L), msg));

        List<MesclarLogs.ParDeEventos> causais = MesclarLogs.paresCausaisPorMensagem(eventos);

        assertEquals(1, causais.size());
        assertEquals(RelacaoCausal.ANTES, causais.get(0).relacao());
    }

    @Test
    void mensagensDiferentesNaoSaoLigadasEntreSi() {
        List<Evento> eventos = List.of(
                eventoVetor("agencia-0", TipoEvento.TRANSFERENCIA_PUBLICADA, List.of(2L, 0L, 0L), Map.of("idMensagem", "A")),
                eventoVetor("agencia-1", TipoEvento.TRANSFERENCIA_CREDITO_REMOTO, List.of(2L, 1L, 0L), Map.of("idMensagem", "B")));

        assertTrue(MesclarLogs.paresCausaisPorMensagem(eventos).isEmpty());
    }

    @Test
    void lerIgnoraLinhasNoFormatoAntigoDoSprint1SemDerrubarAAnalise() throws IOException {
        pastaDeTeste = Files.createTempDirectory("mesclar-logs-teste");
        Files.writeString(pastaDeTeste.resolve("agencia-0.jsonl"),
                "{\"agencia\":\"agencia-0\",\"tipo\":\"DEPOSITO\",\"timestampLamport\":4,\"horaParede\":\"2026-01-01T10:00:00Z\",\"idConta\":0,\"detalhes\":{}}"
                        + System.lineSeparator());
        escreverLinha(pastaDeTeste.resolve("agencia-0.jsonl"), evento("agencia-0", TipoEvento.DEPOSITO, 1));

        List<Evento> eventos = MesclarLogs.lerTodosOsEventos(pastaDeTeste, MesclarLogs.criarObjectMapper());

        assertEquals(1, eventos.size(), "so a linha com relogio vetorial entra na analise");
    }

    private void escreverLinha(Path arquivo, Evento evento) throws IOException {
        String linha = "";
        try {
            linha = objectMapper.writeValueAsString(evento) + System.lineSeparator();
        } catch (Exception e) {
            throw new IOException(e);
        }
        Files.writeString(arquivo, linha, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    private Evento eventoVetor(String agencia, TipoEvento tipo, List<Long> vetor, Map<String, Object> detalhes) {
        return new Evento(agencia, tipo, vetor, Instant.now(), 0L, detalhes);
    }

    private Evento evento(String agencia, TipoEvento tipo, long ts) {
        return new Evento(agencia, tipo, List.of(ts, 0L, 0L), Instant.now(), 0L, Map.of());
    }

    private Evento eventoEm(String agencia, String instante) {
        return new Evento(agencia, TipoEvento.CRIACAO_CONTA, List.of(1L, 0L, 0L), Instant.parse(instante), 0L, Map.of());
    }
}
