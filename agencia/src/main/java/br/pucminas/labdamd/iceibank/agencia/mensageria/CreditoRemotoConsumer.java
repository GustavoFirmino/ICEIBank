/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte C (Publish/Subscribe entre agencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.mensageria;

import br.pucminas.labdamd.iceibank.agencia.common.exceptions.CreditoRemotoNaoAplicavelException;
import br.pucminas.labdamd.iceibank.agencia.transferencia.TransferenciaService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor da fila desta agencia (fila-agencia-{id}). O nome da fila vem de
 * agencia.id (application-agenciaN.yml) - mesma convencao de
 * {@link MensageriaConfig#filaDaAgencia(int)}.
 *
 * Nao passa por nenhum filtro JWT: nao e uma requisicao HTTP, e uma mensagem
 * que ja foi aceita pelo broker. A fronteira de confianca aqui e a credencial
 * do RabbitMQ, nao um token de usuario (discutido na pergunta 3 da Parte C
 * em RESPOSTAS.md).
 *
 * Confirmacao (ack) automatica ao terminar sem excecao. Se
 * {@link TransferenciaService#processarCreditoRemoto} lancar, a mensagem e
 * REJEITADA sem reentrega e o RabbitMQ a encaminha para a dead-letter queue.
 */
@Component
public class CreditoRemotoConsumer {

    private final TransferenciaService transferenciaService;

    public CreditoRemotoConsumer(TransferenciaService transferenciaService) {
        this.transferenciaService = transferenciaService;
    }

    @RabbitListener(queues = "fila-agencia-${agencia.id}")
    public void receber(CreditoRemotoMensagem mensagem) {
        try {
            transferenciaService.processarCreditoRemoto(mensagem);
        } catch (CreditoRemotoNaoAplicavelException erro) {
            // rejeita SEM devolver a fila: reentregar em loop uma mensagem que nunca vai funcionar
            // travaria a fila; vai para a dead-letter queue, com os dados intactos.
            throw new AmqpRejectAndDontRequeueException(erro.getMessage(), erro);
        }
    }
}
