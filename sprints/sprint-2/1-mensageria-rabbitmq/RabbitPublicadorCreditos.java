/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte C (Publish/Subscribe entre agencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.mensageria;

import br.pucminas.labdamd.iceibank.agencia.common.exceptions.MensageriaIndisponivelException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Implementacao real de {@link PublicadorCreditos}, sobre o RabbitTemplate do Spring AMQP. */
@Component
public class RabbitPublicadorCreditos implements PublicadorCreditos {

    // Quanto esperar o broker confirmar que aceitou (e gravou) a mensagem.
    private static final long PRAZO_CONFIRMACAO_MS = 5000;

    private final RabbitTemplate rabbitTemplate;

    public RabbitPublicadorCreditos(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publicar(int idAgenciaDestino, CreditoRemotoMensagem mensagem) {
        String routingKey = MensageriaConfig.routingKeyCreditar(idAgenciaDestino);
        try {
            // invoke(): publica e espera a confirmacao no MESMO canal - sem isso,
            // convertAndSend devolve na hora e "publicada" nao provaria nada.
            rabbitTemplate.invoke(operacoes -> {
                operacoes.convertAndSend(MensageriaConfig.EXCHANGE, routingKey, mensagem, mensagemAmqp -> {
                    // persistent: o RabbitMQ grava em disco - sobrevive a um restart do broker
                    mensagemAmqp.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    mensagemAmqp.getMessageProperties().setMessageId(mensagem.idMensagem());
                    return mensagemAmqp;
                });
                operacoes.waitForConfirmsOrDie(PRAZO_CONFIRMACAO_MS);
                return null;
            });
        } catch (RuntimeException erro) {
            throw new MensageriaIndisponivelException(
                    "Broker RabbitMQ nao aceitou a mensagem para a agencia " + idAgenciaDestino + ": " + erro.getMessage(), erro);
        }
    }
}
