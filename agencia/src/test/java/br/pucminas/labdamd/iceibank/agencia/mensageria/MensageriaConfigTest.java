/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte A/C (topologia do RabbitMQ)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.mensageria;

import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MensageriaConfigTest {

    private final MensageriaConfig config = new MensageriaConfig();
    private final AgenciaProperties agencia0 = new AgenciaProperties(0, 3, 4047);

    @Test
    void exchangeEDoTipoTopicEDuravel() {
        TopicExchange exchange = config.exchangeDeEventos();

        assertEquals("iceibank.eventos", exchange.getName());
        assertEquals("topic", exchange.getType());
        assertTrue(exchange.isDurable());
    }

    @Test
    void cadaAgenciaDeclaraAsTresFilasDuraveisLigadasPelaRoutingKeyDaAgencia() {
        var declaraveis = config.filasDasAgencias(agencia0, config.exchangeDeEventos()).getDeclarables();

        List<Queue> filas = declaraveis.stream().filter(d -> d instanceof Queue).map(d -> (Queue) d).toList();
        List<Binding> ligacoes = declaraveis.stream().filter(d -> d instanceof Binding).map(d -> (Binding) d).toList();

        assertEquals(List.of("fila-agencia-0", "fila-agencia-1", "fila-agencia-2"), filas.stream().map(Queue::getName).toList());
        assertTrue(filas.stream().allMatch(Queue::isDurable), "fila nao duravel perde as mensagens se o broker reiniciar");
        assertEquals(List.of("agencia.0.creditar", "agencia.1.creditar", "agencia.2.creditar"),
                ligacoes.stream().map(Binding::getRoutingKey).toList());
        assertTrue(ligacoes.stream().allMatch(l -> l.getExchange().equals("iceibank.eventos")));
    }

    @Test
    void asFilasDasAgenciasApontamParaADeadLetterExchange() {
        var declaraveis = config.filasDasAgencias(agencia0, config.exchangeDeEventos()).getDeclarables();

        declaraveis.stream().filter(d -> d instanceof Queue).map(d -> (Queue) d).forEach(fila -> {
            assertEquals("iceibank.eventos.dlx", fila.getArguments().get("x-dead-letter-exchange"), fila.getName());
            assertEquals("creditar.morta", fila.getArguments().get("x-dead-letter-routing-key"), fila.getName());
        });
    }

    @Test
    void filaDeMensagensMortasEDuravelELigadaAExchangeDeMortas() {
        var declaraveis = config.filaDeMensagensMortas(config.exchangeDeMensagensMortas()).getDeclarables();

        Queue fila = declaraveis.stream().filter(d -> d instanceof Queue).map(d -> (Queue) d).findFirst().orElseThrow();
        Binding ligacao = declaraveis.stream().filter(d -> d instanceof Binding).map(d -> (Binding) d).findFirst().orElseThrow();
        assertEquals("fila-creditos-mortos", fila.getName());
        assertTrue(fila.isDurable());
        assertEquals("iceibank.eventos.dlx", ligacao.getExchange());
        assertEquals("creditar.morta", ligacao.getRoutingKey());
    }

    @Test
    void todasAsAgenciasDeclaramTodasAsFilas_umaMensagemParaAgenciaQueNuncaSubiuNaoEDescartada() {
        var declaraveis = config.filasDasAgencias(new AgenciaProperties(2, 3, 4047), config.exchangeDeEventos()).getDeclarables();

        long filas = declaraveis.stream().filter(d -> d instanceof Queue).count();
        assertEquals(3, filas, "a agencia 2 tambem declara as filas das agencias 0 e 1");
    }

    @Test
    void mensagemViajaComoJsonLegivelEVoltaComOMesmoConteudo() throws Exception {
        Jackson2JsonMessageConverter conversor = (Jackson2JsonMessageConverter) config.conversorJson();
        var original = new CreditoRemotoMensagem("id-1", 7, 250, List.of(3L, 1L, 0L), 0, 4);

        Message mensagem = conversor.toMessage(original, new MessageProperties());
        String json = new String(mensagem.getBody(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"vetorEnvio\":[3,1,0]"), json);
        assertTrue(json.contains("\"idMensagem\":\"id-1\""), json);

        // o tipo vem do parametro do listener (INFERRED), nao do cabecalho __TypeId__
        mensagem.getMessageProperties().setInferredArgumentType(CreditoRemotoMensagem.class);
        Object lida = conversor.fromMessage(mensagem);
        assertEquals(original, lida);
    }
}
