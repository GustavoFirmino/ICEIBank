/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte A/C (RabbitMQ e Publish/Subscribe)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.mensageria;

import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Topologia do RabbitMQ (roteiro, Parte A):
 *
 *  - UMA exchange do tipo topic, duravel: {@value #EXCHANGE}. E para ela que
 *    as agencias publicam - nunca direto para uma fila.
 *  - UMA fila duravel por agencia (fila-agencia-0/1/2), ligada a exchange pela
 *    routing key agencia.&lt;id&gt;.creditar. Quem publica com
 *    "agencia.1.creditar" so alcanca a fila da agencia 1, mesmo a exchange
 *    sendo compartilhada - e isso que e publish/subscribe: quem publica nao
 *    sabe (nem precisa saber) quem vai consumir.
 *  - Funcionalidade adicional (dead-letter): cada fila de agencia aponta para
 *    a exchange {@value #DEAD_LETTER_EXCHANGE}; uma mensagem que a agencia
 *    REJEITA (ex.: credito para uma conta que nao existe) nao e descartada
 *    nem reentregue em loop - vai para {@value #FILA_MORTAS}, onde fica
 *    retida para inspecao e reprocessamento.
 *
 * TODAS as agencias declaram as filas de TODAS as agencias na subida
 * (declarar e idempotente no RabbitMQ). Assim, uma mensagem publicada para uma
 * agencia que ainda nunca subiu tambem fica retida em vez de ser descartada
 * por "nenhuma fila ligada a essa routing key".
 */
@Configuration
public class MensageriaConfig {

    public static final String EXCHANGE = "iceibank.eventos";
    public static final String DEAD_LETTER_EXCHANGE = "iceibank.eventos.dlx";
    public static final String FILA_MORTAS = "fila-creditos-mortos";
    public static final String ROUTING_KEY_MORTA = "creditar.morta";

    public static String filaDaAgencia(int idAgencia) {
        return "fila-agencia-" + idAgencia;
    }

    public static String routingKeyCreditar(int idAgencia) {
        return "agencia." + idAgencia + ".creditar";
    }

    @Bean
    public TopicExchange exchangeDeEventos() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange exchangeDeMensagensMortas() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Declarables filaDeMensagensMortas(DirectExchange exchangeDeMensagensMortas) {
        Queue fila = QueueBuilder.durable(FILA_MORTAS).build();
        Binding ligacao = BindingBuilder.bind(fila).to(exchangeDeMensagensMortas).with(ROUTING_KEY_MORTA);
        return new Declarables(fila, ligacao);
    }

    @Bean
    public Declarables filasDasAgencias(AgenciaProperties agenciaProperties, TopicExchange exchangeDeEventos) {
        List<Declarable> declaraveis = new ArrayList<>();
        for (int id = 0; id < agenciaProperties.totalAgencias(); id++) {
            Queue fila = QueueBuilder.durable(filaDaAgencia(id))
                    .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                    .deadLetterRoutingKey(ROUTING_KEY_MORTA)
                    .build();
            declaraveis.add(fila);
            declaraveis.add(BindingBuilder.bind(fila).to(exchangeDeEventos).with(routingKeyCreditar(id)));
        }
        return new Declarables(declaraveis);
    }

    /**
     * Mensagens em JSON (legivel no painel do RabbitMQ e independente de
     * linguagem - uma agencia escrita em Python poderia consumir a mesma
     * fila). O tipo Java e INFERIDO do parametro do listener, e nao lido do
     * cabecalho __TypeId__ (que carregaria o nome de uma classe Java).
     */
    @Bean
    public MessageConverter conversorJson() {
        Jackson2JsonMessageConverter conversor = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper mapeador = new DefaultJackson2JavaTypeMapper();
        mapeador.setTypePrecedence(DefaultJackson2JavaTypeMapper.TypePrecedence.INFERRED);
        conversor.setJavaTypeMapper(mapeador);
        return conversor;
    }
}
