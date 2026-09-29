/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia;

import br.pucminas.labdamd.iceibank.agencia.clock.RelogioVetorialService;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.AgenciaDestinoIndisponivelException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ComunicacaoAgenciaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaNaoEncontradaException;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.conta.Conta;
import br.pucminas.labdamd.iceibank.agencia.conta.ContaRepository;
import br.pucminas.labdamd.iceibank.agencia.eventlog.EventLogService;
import br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.CreditarRemotoResponse;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.TransferenciaRequest;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.TransferenciaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TransferenciaService {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

    private final AgenciaProperties agenciaProperties;
    private final ContaRepository contaRepository;
    private final RelogioVetorialService relogio;
    private final EventLogService eventLog;
    private final RemoteBranchClient remoteBranchClient;
    private final IdempotencyStore idempotencyStore;

    public TransferenciaService(AgenciaProperties agenciaProperties, ContaRepository contaRepository,
                                 RelogioVetorialService relogio, EventLogService eventLog,
                                 RemoteBranchClient remoteBranchClient, IdempotencyStore idempotencyStore) {
        this.agenciaProperties = agenciaProperties;
        this.contaRepository = contaRepository;
        this.relogio = relogio;
        this.eventLog = eventLog;
        this.remoteBranchClient = remoteBranchClient;
        this.idempotencyStore = idempotencyStore;
    }

    public TransferenciaResponse transferir(TransferenciaRequest request) {
        String idOperacao = request.idOperacao();

        // Funcionalidade adicional: idempotencia. Se o cliente reenviar a
        // MESMA operacao (mesmo idOperacao) - inclusive concorrentemente, ou
        // depois de uma falha - nao aplicamos de novo. Ver IdempotencyStore
        // para o porque de nao ser um simples "buscar, depois registrar".
        if (idOperacao == null) {
            return executarTransferencia(request);
        }
        return idempotencyStore.executarUmaVezSo(idOperacao, () -> executarTransferencia(request));
    }

    private TransferenciaResponse executarTransferencia(TransferenciaRequest request) {
        long idOrigem = request.idOrigem();
        long idDestino = request.idDestino();
        long valor = request.valor();

        Conta contaOrigem = contaRepository.buscar(idOrigem)
                .orElseThrow(() -> new ContaNaoEncontradaException(idOrigem));

        // O debito e sempre local, pois esta agencia e a dona da conta de origem.
        // sacar() ja valida saldo suficiente (lanca SaldoInsuficienteException e
        // nao consome tick do relogio se falhar).
        contaOrigem.sacar(valor);
        List<Long> vetorDebito = relogio.eventoLocal();
        eventLog.registrar(TipoEvento.TRANSFERENCIA_DEBITO, vetorDebito, idOrigem,
                Map.of("idDestino", idDestino, "valor", valor));

        int agenciaDestino = agenciaProperties.agenciaResponsavel(idDestino);

        if (agenciaDestino == agenciaProperties.id()) {
            return transferirLocal(idOrigem, idDestino, valor, contaOrigem);
        }
        return transferirEntreAgencias(idOrigem, idDestino, valor, contaOrigem, agenciaDestino);
    }

    private TransferenciaResponse transferirLocal(long idOrigem, long idDestino, long valor, Conta contaOrigem) {
        var contaDestino = contaRepository.buscar(idDestino);
        if (contaDestino.isEmpty()) {
            // conta de destino nao existe nesta (mesma) agencia - como e tudo
            // local (mesma JVM), podemos desfazer o debito com seguranca. Mas
            // o estorno em si tambem precisa virar um evento (com seu proprio
            // tick do relogio vetorial) - senao o log mostra um debito sem contrapartida,
            // como se o dinheiro tivesse sumido de verdade.
            contaOrigem.depositar(valor);
            List<Long> vetorEstorno = relogio.eventoLocal();
            eventLog.registrar(TipoEvento.TRANSFERENCIA_REVERTIDA, vetorEstorno, idOrigem,
                    Map.of("idDestino", idDestino, "valor", valor, "motivo", "conta de destino nao encontrada"));
            throw new ContaNaoEncontradaException(idDestino);
        }

        contaDestino.get().depositar(valor);
        List<Long> vetorCredito = relogio.eventoLocal();
        eventLog.registrar(TipoEvento.TRANSFERENCIA_CREDITO, vetorCredito, idDestino,
                Map.of("idOrigem", idOrigem, "valor", valor));

        return TransferenciaResponse.nova("Transferencia concluida (mesma agencia).");
    }

    private TransferenciaResponse transferirEntreAgencias(long idOrigem, long idDestino, long valor,
                                                            Conta contaOrigem, int agenciaDestino) {
        // Ao ENVIAR uma mensagem para outra agencia, o relogio vetorial e
        // incrementado e o vetor INTEIRO e anexado a requisicao - e a regra 2 do algoritmo.
        List<Long> vetorEnvio = relogio.aoEnviar();

        try {
            remoteBranchClient.creditarRemoto(agenciaDestino, idDestino, valor, vetorEnvio, agenciaProperties.id());
            return TransferenciaResponse.nova("Transferencia concluida (entre agencias).");
        } catch (ComunicacaoAgenciaException erro) {
            // Captura especificamente falha de COMUNICACAO (rede, timeout, resposta de
            // erro) - nao RuntimeException generico, para nao mascarar um bug real
            // (ex.: NullPointerException) como se fosse "agencia de destino indisponivel".
            // LIMITACAO CONHECIDA: o debito acima NAO e revertido - o dinheiro
            // "desaparece" temporariamente. Ver AgenciaDestinoIndisponivelException.
            log.warn("Falha ao contatar agencia {} para creditar conta {}: {}", agenciaDestino, idDestino, erro.getMessage());
            List<Long> vetorFalha = relogio.eventoLocal();
            eventLog.registrar(TipoEvento.TRANSFERENCIA_FALHOU, vetorFalha, idOrigem,
                    Map.of("idDestino", idDestino, "valor", valor, "erro", String.valueOf(erro.getMessage())));
            throw new AgenciaDestinoIndisponivelException(
                    "Falha ao contatar agencia de destino. Debito ja aplicado - inconsistencia conhecida (ver Sprint 4).");
        }
    }

    /**
     * Chamado pela agencia de ORIGEM na agencia de DESTINO via RemoteBranchClient.
     * Ao RECEBER a mensagem, o relogio vetorial e ajustado com base no
     * vetor recebido (max posicao a posicao + incremento proprio) - e a regra 3 do algoritmo.
     */
    public CreditarRemotoResponse creditarRemoto(long idConta, long valor, List<Long> vetorEnvio, int origemAgencia) {
        Conta conta = contaRepository.buscar(idConta).orElseThrow(() -> new ContaNaoEncontradaException(idConta));

        List<Long> vetor = relogio.aoReceber(vetorEnvio);
        conta.depositar(valor);
        eventLog.registrar(TipoEvento.TRANSFERENCIA_CREDITO_REMOTO, vetor, idConta,
                Map.of("valor", valor, "origemAgencia", origemAgencia));

        return new CreditarRemotoResponse("Credito remoto aplicado.", conta.saldo());
    }
}
