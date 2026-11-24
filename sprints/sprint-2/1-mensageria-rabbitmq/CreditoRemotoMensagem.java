/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte C (Publish/Subscribe entre agencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.mensageria;

import java.util.List;

/**
 * O evento publicado pela agencia de ORIGEM na exchange {@code iceibank.eventos}
 * (routing key {@code agencia.<destino>.creditar}) e consumido pela agencia de
 * DESTINO. Substitui o corpo da chamada REST creditar-remoto do Sprint 1.
 *
 * Os quatro primeiros campos sao os do roteiro (idConta, valor, vetorEnvio,
 * origemAgencia). Os outros dois sao acrescentados por este projeto:
 *
 *  - idMensagem: identificador UNICO desta mensagem (UUID gerado por quem
 *    publica). O RabbitMQ garante entrega "pelo menos uma vez" - a mesma
 *    mensagem pode chegar duas vezes (ex.: o consumidor cai depois de aplicar
 *    o credito e antes de confirmar) e, sem esse id, o credito seria aplicado
 *    duas vezes. Tambem liga, no log, o evento de envio ao de recebimento.
 *  - idContaOrigem: so para rastreabilidade (auditoria e historico).
 *
 * @param idMensagem    UUID unico da mensagem (base da idempotencia do consumidor)
 * @param idConta       conta de DESTINO a ser creditada (pertence a agencia que consome)
 * @param valor         valor a creditar
 * @param vetorEnvio    relogio vetorial da origem no instante do envio (regra 2)
 * @param origemAgencia id da agencia que publicou
 * @param idContaOrigem conta debitada na origem
 */
public record CreditoRemotoMensagem(
        String idMensagem,
        long idConta,
        long valor,
        List<Long> vetorEnvio,
        int origemAgencia,
        long idContaOrigem
) {
}
