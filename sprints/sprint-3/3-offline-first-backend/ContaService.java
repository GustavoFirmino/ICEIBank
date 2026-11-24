/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte C (API REST/MVC de contas)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.conta;

import br.pucminas.labdamd.iceibank.agencia.clock.RelogioVetorialService;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaDuplicadaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaNaoEncontradaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.DadosInvalidosException;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.conta.dto.ContaResponse;
import br.pucminas.labdamd.iceibank.agencia.conta.dto.CriarContaRequest;
import br.pucminas.labdamd.iceibank.agencia.conta.dto.HistoricoEventoResponse;
import br.pucminas.labdamd.iceibank.agencia.eventlog.EventLogService;
import br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento;
import br.pucminas.labdamd.iceibank.agencia.clock.RelacaoCausal;
import br.pucminas.labdamd.iceibank.agencia.clock.Vetores;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ConflitoSincronizacaoException;
import br.pucminas.labdamd.iceibank.agencia.conta.dto.ContaComVetorResponse;
import br.pucminas.labdamd.iceibank.agencia.conta.dto.SincronizarRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ContaService {

    private final AgenciaProperties agenciaProperties;
    private final ContaRepository contaRepository;
    private final RelogioVetorialService relogio;
    private final EventLogService eventLog;

    public ContaService(AgenciaProperties agenciaProperties, ContaRepository contaRepository,
                         RelogioVetorialService relogio, EventLogService eventLog) {
        this.agenciaProperties = agenciaProperties;
        this.contaRepository = contaRepository;
        this.relogio = relogio;
        this.eventLog = eventLog;
    }

    public ContaResponse criarConta(CriarContaRequest request) {
        if (request.id() == null) {
            throw new DadosInvalidosException("O campo 'id' e obrigatorio para criar uma conta.");
        }
        long id = request.id();
        agenciaProperties.validarParticaoOuLancar(id);

        long saldoInicial = request.saldoInicial() != null ? request.saldoInicial() : 0L;
        Conta conta = new Conta(id, request.titular(), saldoInicial);

        if (!contaRepository.salvarSeNaoExiste(conta)) {
            throw new ContaDuplicadaException(id);
        }

        List<Long> vetor = relogio.eventoLocal();
        eventLog.registrar(TipoEvento.CRIACAO_CONTA, vetor, id,
                Map.of("titular", request.titular(), "saldoInicial", saldoInicial));

        return ContaResponse.de(conta);
    }

    public ContaResponse consultarSaldo(long id) {
        return ContaResponse.de(buscarOuLancar(id));
    }

    /** Sprint 3: saldo + vetor atual da agencia, para o app guardar no cache offline. */
    public ContaComVetorResponse consultarSaldoComVetor(long id) {
        Conta conta = buscarOuLancar(id);
        return ContaComVetorResponse.de(conta, relogio.valorAtual());
    }

    /**
     * Sprint 3 (offline-first): aplica uma operacao feita offline pelo app, SE o vetor da agencia ainda for
     * igual ao que o app conhecia (nada aconteceu na agencia desde a ultima leitura). Se o vetor avancou,
     * recusa com 409 e devolve o estado atual - quem decide o que fazer e o app/usuario.
     *
     * Simplificacao assumida (pergunta 9.6.2): o vetor e por AGENCIA, nao por conta; qualquer evento na
     * agencia (ate de outra conta, ou uma eleicao do Bully) conta como conflito. Entre o teste do vetor e o
     * tick do relogio existe uma janela minima em que uma operacao concorrente poderia entrar; a
     * sincronizacao em si e serializada por este metodo ser synchronized.
     */
    public synchronized ContaComVetorResponse sincronizarOperacaoPendente(long id, SincronizarRequest request) {
        Conta conta = buscarOuLancar(id);
        List<Long> vetorAtual = relogio.valorAtual();
        List<Long> conhecido = request.vetorConhecido();
        if (conhecido == null || conhecido.size() != vetorAtual.size()) {
            throw new DadosInvalidosException("O campo 'vetorConhecido' e obrigatorio e precisa ter "
                    + vetorAtual.size() + " posicoes.");
        }
        if (Vetores.comparar(conhecido, vetorAtual) != RelacaoCausal.IGUAIS) {
            throw new ConflitoSincronizacaoException(conhecido, vetorAtual, ContaComVetorResponse.de(conta, vetorAtual));
        }

        TipoEvento tipoOperacao;
        if ("DEPOSITO".equals(request.tipo())) {
            conta.depositar(request.valor());
        } else if ("SAQUE".equals(request.tipo())) {
            conta.sacar(request.valor()); // 400 se faltar saldo (antes de consumir tick do relogio)
        } else {
            throw new DadosInvalidosException("Tipo de operacao pendente nao suportado: " + request.tipo());
        }

        List<Long> vetor = relogio.eventoLocal();
        eventLog.registrar(TipoEvento.SINCRONIZACAO_OFFLINE, vetor, id,
                Map.of("tipo", request.tipo(), "valor", request.valor(), "novoSaldo", conta.saldo()));
        return ContaComVetorResponse.de(conta, vetor);
    }

    public ContaResponse depositar(long id, long valor) {
        Conta conta = buscarOuLancar(id);
        conta.depositar(valor);

        List<Long> vetor = relogio.eventoLocal();
        eventLog.registrar(TipoEvento.DEPOSITO, vetor, id, Map.of("valor", valor, "novoSaldo", conta.saldo()));

        return ContaResponse.de(conta);
    }

    public ContaResponse sacar(long id, long valor) {
        Conta conta = buscarOuLancar(id);
        conta.sacar(valor); // lanca SaldoInsuficienteException se nao houver saldo - nao consome tick do relogio

        List<Long> vetor = relogio.eventoLocal();
        eventLog.registrar(TipoEvento.SAQUE, vetor, id, Map.of("valor", valor, "novoSaldo", conta.saldo()));

        return ContaResponse.de(conta);
    }

    /**
     * Funcionalidade adicional: historico de transacoes por conta. Lista os
     * eventos ja registrados para esta conta (criacao, depositos, saques,
     * transferencias enviadas/recebidas), na ordem em que aconteceram
     * NESTA agencia.
     */
    public List<HistoricoEventoResponse> historico(long id) {
        if (!contaRepository.existe(id)) { // 404 se a conta nao existe (nesta agencia)
            throw new ContaNaoEncontradaException(id);
        }
        return eventLog.historicoDaConta(id).stream()
                .map(HistoricoEventoResponse::de)
                .toList();
    }

    Conta buscarOuLancar(long id) {
        return contaRepository.buscar(id).orElseThrow(() -> new ContaNaoEncontradaException(id));
    }
}
