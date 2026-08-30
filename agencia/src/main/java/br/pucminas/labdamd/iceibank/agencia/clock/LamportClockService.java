/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte B (Relogio de Lamport)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.clock;

import org.springframework.stereotype.Service;

/**
 * Relogio logico de Lamport - um contador inteiro por processo (aqui, por
 * agencia), com as tres regras classicas:
 *
 * 1. Antes de qualquer evento local, o processo incrementa seu contador.
 * 2. Ao enviar uma mensagem, o processo incrementa o contador e anexa o
 *    valor a mensagem.
 * 3. Ao receber uma mensagem com timestamp t, o processo ajusta seu
 *    contador para max(contador_local, t) + 1.
 *
 * O metodo "synchronized" importa aqui: um servidor Spring Boot atende
 * requisicoes em varias threads ao mesmo tempo, e o contador e um estado
 * compartilhado entre elas - sem sincronizacao, duas requisicoes simultaneas
 * poderiam ler/incrementar o contador de forma inconsistente (a mesma
 * condicao de corrida vista no laboratorio de Threads).
 */
@Service
public class LamportClockService {

    private long contador = 0;

    public synchronized long eventoLocal() {
        contador += 1;
        return contador;
    }

    public synchronized long aoEnviar() {
        contador += 1;
        return contador;
    }

    public synchronized long aoReceber(long timestampRecebido) {
        contador = Math.max(contador, timestampRecebido) + 1;
        return contador;
    }

    public synchronized long valorAtual() {
        return contador;
    }
}
