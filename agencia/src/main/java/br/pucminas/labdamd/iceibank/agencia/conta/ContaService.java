/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte C (API REST/MVC de contas)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.conta;

import br.pucminas.labdamd.iceibank.agencia.clock.LamportClockService;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaDuplicadaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaNaoEncontradaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.DadosInvalidosException;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.conta.dto.ContaResponse;
import br.pucminas.labdamd.iceibank.agencia.conta.dto.CriarContaRequest;
import br.pucminas.labdamd.iceibank.agencia.eventlog.EventLogService;
import br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ContaService {

    private final AgenciaProperties agenciaProperties;
    private final ContaRepository contaRepository;
    private final LamportClockService relogio;
    private final EventLogService eventLog;

    public ContaService(AgenciaProperties agenciaProperties, ContaRepository contaRepository,
                         LamportClockService relogio, EventLogService eventLog) {
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

        long ts = relogio.eventoLocal();
        eventLog.registrar(TipoEvento.CRIACAO_CONTA, ts, id,
                Map.of("titular", request.titular(), "saldoInicial", saldoInicial));

        return ContaResponse.de(conta);
    }

    public ContaResponse consultarSaldo(long id) {
        return ContaResponse.de(buscarOuLancar(id));
    }

    public ContaResponse depositar(long id, long valor) {
        Conta conta = buscarOuLancar(id);
        conta.depositar(valor);

        long ts = relogio.eventoLocal();
        eventLog.registrar(TipoEvento.DEPOSITO, ts, id, Map.of("valor", valor, "novoSaldo", conta.saldo()));

        return ContaResponse.de(conta);
    }

    public ContaResponse sacar(long id, long valor) {
        Conta conta = buscarOuLancar(id);
        conta.sacar(valor); // lanca SaldoInsuficienteException se nao houver saldo - nao consome tick do relogio

        long ts = relogio.eventoLocal();
        eventLog.registrar(TipoEvento.SAQUE, ts, id, Map.of("valor", valor, "novoSaldo", conta.saldo()));

        return ContaResponse.de(conta);
    }

    Conta buscarOuLancar(long id) {
        return contaRepository.buscar(id).orElseThrow(() -> new ContaNaoEncontradaException(id));
    }
}
