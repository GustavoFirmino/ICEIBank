/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias) / Sprint 2 - Parte C (Publish/Subscribe)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia;

import br.pucminas.labdamd.iceibank.agencia.clock.RelogioVetorialService;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaNaoEncontradaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.CreditoRemotoNaoAplicavelException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.MensageriaIndisponivelException;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.conta.Conta;
import br.pucminas.labdamd.iceibank.agencia.conta.ContaRepository;
import br.pucminas.labdamd.iceibank.agencia.eventlog.EventLogService;
import br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento;
import br.pucminas.labdamd.iceibank.agencia.mensageria.CreditoRemotoMensagem;
import br.pucminas.labdamd.iceibank.agencia.mensageria.PublicadorCreditos;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.TransferenciaRequest;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.TransferenciaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TransferenciaService {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

    private final AgenciaProperties agenciaProperties;
    private final ContaRepository contaRepository;
    private final RelogioVetorialService relogio;
    private final EventLogService eventLog;
    private final PublicadorCreditos publicadorCreditos;
    private final IdempotencyStore idempotencyStore;

    // idMensagem de todo credito remoto JA APLICADO por esta agencia. E o que impede a mesma
    // mensagem, entregue duas vezes pelo broker, de creditar a conta duas vezes. Em memoria,
    // como as contas: some junto com elas no restart (ver RESPOSTAS.md).
    private final Set<String> mensagensAplicadas = ConcurrentHashMap.newKeySet();

    public TransferenciaService(AgenciaProperties agenciaProperties, ContaRepository contaRepository,
                                 RelogioVetorialService relogio, EventLogService eventLog,
                                 PublicadorCreditos publicadorCreditos, IdempotencyStore idempotencyStore) {
        this.agenciaProperties = agenciaProperties;
        this.contaRepository = contaRepository;
        this.relogio = relogio;
        this.eventLog = eventLog;
        this.publicadorCreditos = publicadorCreditos;
        this.idempotencyStore = idempotencyStore;
    }

    public TransferenciaResponse transferir(TransferenciaRequest request) {
        String idOperacao = request.idOperacao();

        // Funcionalidade adicional do Sprint 1: idempotencia da REQUISICAO HTTP. Se o cliente
        // reenviar a MESMA operacao (mesmo idOperacao) - inclusive concorrentemente - nao
        // aplicamos de novo. (A idempotencia do CONSUMIDOR, por idMensagem, e outra camada.)
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
            // conta de destino nao existe nesta (mesma) agencia - como e tudo local (mesma JVM),
            // podemos desfazer o debito com seguranca, registrando o estorno como evento proprio.
            estornarDebito(idOrigem, idDestino, valor, contaOrigem, "conta de destino nao encontrada");
            throw new ContaNaoEncontradaException(idDestino);
        }

        contaDestino.get().depositar(valor);
        List<Long> vetorCredito = relogio.eventoLocal();
        eventLog.registrar(TipoEvento.TRANSFERENCIA_CREDITO, vetorCredito, idDestino,
                Map.of("idOrigem", idOrigem, "valor", valor));

        return TransferenciaResponse.nova("Transferencia concluida (mesma agencia).");
    }

    /**
     * Sprint 2: em vez da chamada REST direta do Sprint 1, PUBLICA um evento na exchange do
     * RabbitMQ. A agencia de destino consome quando puder - inclusive se estiver fora do ar
     * agora (a fila e duravel e a mensagem, persistente).
     *
     * O que "sucesso" significa mudou: antes, 200 = "o credito ja foi aplicado la". Agora,
     * 200 = "o broker aceitou a mensagem". A aplicacao do credito acontece depois, em um
     * momento que quem chamou nao controla nem confirma na hora.
     */
    private TransferenciaResponse transferirEntreAgencias(long idOrigem, long idDestino, long valor,
                                                            Conta contaOrigem, int agenciaDestino) {
        // Ao ENVIAR uma mensagem, o relogio vetorial e incrementado e o vetor INTEIRO
        // e anexado a ela - regra 2 do algoritmo.
        List<Long> vetorEnvio = relogio.aoEnviar();
        String idMensagem = UUID.randomUUID().toString();
        var mensagem = new CreditoRemotoMensagem(
                idMensagem, idDestino, valor, vetorEnvio, agenciaProperties.id(), idOrigem);

        try {
            publicadorCreditos.publicar(agenciaDestino, mensagem);
        } catch (MensageriaIndisponivelException erro) {
            // O broker nao aceitou: NADA foi publicado, entao desfazer o debito e seguro
            // (diferente do Sprint 1, onde a falha deixava o debito pendurado).
            log.warn("Broker indisponivel ao publicar credito para a agencia {}: {}", agenciaDestino, erro.getMessage());
            estornarDebito(idOrigem, idDestino, valor, contaOrigem, "broker indisponivel: " + erro.getMessage());
            throw erro;
        }

        eventLog.registrar(TipoEvento.TRANSFERENCIA_PUBLICADA, vetorEnvio, idOrigem,
                Map.of("idMensagem", idMensagem, "idDestino", idDestino, "valor", valor,
                        "agenciaDestino", agenciaDestino));

        return TransferenciaResponse.nova("Transferencia publicada para a agencia de destino (entrega assincrona).");
    }

    private void estornarDebito(long idOrigem, long idDestino, long valor, Conta contaOrigem, String motivo) {
        contaOrigem.depositar(valor);
        List<Long> vetorEstorno = relogio.eventoLocal();
        eventLog.registrar(TipoEvento.TRANSFERENCIA_REVERTIDA, vetorEstorno, idOrigem,
                Map.of("idDestino", idDestino, "valor", valor, "motivo", motivo));
    }

    /**
     * Chamado pelo consumidor da fila desta agencia (CreditoRemotoConsumer) para cada credito
     * remoto recebido. O RabbitMQ garante entrega "pelo menos uma vez", entao este metodo e
     * idempotente por idMensagem.
     *
     * synchronized: "ja apliquei esta mensagem?" + "aplicar" precisam ser um passo atomico,
     * senao duas entregas simultaneas da mesma mensagem poderiam passar ambas pela checagem.
     *
     * @throws CreditoRemotoNaoAplicavelException se a conta nao existe aqui (a mensagem vai
     *         para a dead-letter queue - ver CreditoRemotoConsumer)
     */
    public synchronized void processarCreditoRemoto(CreditoRemotoMensagem mensagem) {
        if (mensagensAplicadas.contains(mensagem.idMensagem())) {
            // entrega duplicada: NAO aplica de novo e NAO conta como evento causal novo
            // (nao mexe no relogio), so deixa o rastro para auditoria.
            eventLog.registrar(TipoEvento.CREDITO_REMOTO_DUPLICADO, relogio.valorAtual(), mensagem.idConta(),
                    Map.of("idMensagem", mensagem.idMensagem(), "valor", mensagem.valor(),
                            "origemAgencia", mensagem.origemAgencia()));
            return;
        }

        // Ao RECEBER uma mensagem, o relogio vetorial faz max posicao a posicao com o vetor
        // recebido e incrementa a propria posicao - regra 3. Receber e um evento mesmo que
        // depois o credito nao possa ser aplicado.
        List<Long> vetorRecebimento = relogio.aoReceber(mensagem.vetorEnvio());

        var conta = contaRepository.buscar(mensagem.idConta());
        if (conta.isEmpty()) {
            eventLog.registrar(TipoEvento.CREDITO_REMOTO_FALHOU, vetorRecebimento, mensagem.idConta(),
                    Map.of("idMensagem", mensagem.idMensagem(), "valor", mensagem.valor(),
                            "origemAgencia", mensagem.origemAgencia(),
                            "motivo", "conta nao encontrada nesta agencia"));
            throw new CreditoRemotoNaoAplicavelException(
                    "Conta " + mensagem.idConta() + " nao encontrada nesta agencia (mensagem " + mensagem.idMensagem() + ").");
        }

        conta.get().depositar(mensagem.valor());
        // so marca como aplicada DEPOIS de aplicar: se falhar (conta inexistente), a mesma mensagem
        // pode ser reprocessada a partir da dead-letter queue quando a conta existir.
        mensagensAplicadas.add(mensagem.idMensagem());
        eventLog.registrar(TipoEvento.TRANSFERENCIA_CREDITO_REMOTO, vetorRecebimento, mensagem.idConta(),
                Map.of("idMensagem", mensagem.idMensagem(), "valor", mensagem.valor(),
                        "origemAgencia", mensagem.origemAgencia(), "idOrigem", mensagem.idContaOrigem()));
    }
}
