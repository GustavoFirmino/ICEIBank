/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Funcionalidade adicional (dead-letter queue com reprocessamento)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.mensageria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Implementacao real sobre basicGet/basicAck/basicNack do cliente do RabbitMQ. */
@Component
public class RabbitFilaDeMensagensMortas implements FilaDeMensagensMortas {

    private static final Logger log = LoggerFactory.getLogger(RabbitFilaDeMensagensMortas.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RabbitFilaDeMensagensMortas(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void percorrer(int max, Visitante visitante) {
        rabbitTemplate.execute(canal -> {
            List<Long> paraDevolver = new ArrayList<>();
            for (int lidas = 0; lidas < max; lidas++) {
                GetResponse resposta = canal.basicGet(MensageriaConfig.FILA_MORTAS, false);
                if (resposta == null) {
                    break; // fila vazia (as ainda nao confirmadas nem devolvidas nao contam)
                }
                long tag = resposta.getEnvelope().getDeliveryTag();
                Decisao decisao = Decisao.DEVOLVER;
                try {
                    var mensagem = objectMapper.readValue(resposta.getBody(), CreditoRemotoMensagem.class);
                    decisao = visitante.visitar(mensagem);
                } catch (Exception erro) {
                    log.warn("Mensagem morta ilegivel ou falha ao tratar - fica na fila: {}", erro.getMessage());
                }
                if (decisao == Decisao.CONFIRMAR) {
                    canal.basicAck(tag, false);
                } else {
                    // NAO devolve agora: reenfileirar no meio do laco faria o proximo basicGet
                    // devolver a mesma mensagem de novo, para sempre.
                    paraDevolver.add(tag);
                }
            }
            for (long tag : paraDevolver) {
                canal.basicNack(tag, false, true);
            }
            return null;
        });
    }
}
