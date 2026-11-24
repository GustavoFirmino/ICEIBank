/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte B (Relogio vetorial)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.clock;

import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Relogio vetorial: um vetor de contadores, UMA POSICAO POR AGENCIA (3 posicoes
 * no ICEIBank), no lugar do contador unico de Lamport do Sprint 1. Tres regras:
 *
 * 1. Evento local: incrementa a PROPRIA posicao do vetor.
 * 2. Ao ENVIAR uma mensagem: incrementa a propria posicao e anexa o vetor
 *    INTEIRO a mensagem.
 * 3. Ao RECEBER uma mensagem com vetor V: para cada posicao i,
 *    vetor[i] = max(vetor[i], V[i]); depois incrementa a propria posicao.
 *
 * Com dois vetores V1 e V2 (ver {@link Vetores#comparar}): V1 <= V2 em toda
 * posicao (e diferentes) => o evento de V1 aconteceu ANTES; se nenhum domina o
 * outro => os eventos sao CONCORRENTES. E essa comparacao confiavel que o
 * relogio de Lamport nao dava.
 *
 * Todos os metodos sao synchronized: o vetor e estado compartilhado entre as
 * threads HTTP (Tomcat) e as threads do consumidor RabbitMQ - sem isso, um
 * evento local e um recebimento simultaneos poderiam perder um incremento.
 * Os metodos devolvem COPIAS imutaveis, para que o vetor gravado em um evento
 * nunca mude depois que o relogio avanca.
 */
@Service
public class RelogioVetorialService {

    private final int idAgencia;
    private final long[] vetor;

    @Autowired
    public RelogioVetorialService(AgenciaProperties agenciaProperties) {
        this(agenciaProperties.id(), agenciaProperties.totalAgencias());
    }

    public RelogioVetorialService(int idAgencia, int totalAgencias) {
        if (totalAgencias <= 0 || idAgencia < 0 || idAgencia >= totalAgencias) {
            throw new IllegalArgumentException(
                    "Agencia " + idAgencia + " invalida para um vetor de " + totalAgencias + " posicoes.");
        }
        this.idAgencia = idAgencia;
        this.vetor = new long[totalAgencias];
    }

    /** Regra 1. */
    public synchronized List<Long> eventoLocal() {
        vetor[idAgencia] += 1;
        return copia();
    }

    /** Regra 2 - o vetor devolvido e o que deve ser anexado a mensagem. */
    public synchronized List<Long> aoEnviar() {
        vetor[idAgencia] += 1;
        return copia();
    }

    /** Regra 3. */
    public synchronized List<Long> aoReceber(List<Long> vetorRecebido) {
        if (vetorRecebido == null || vetorRecebido.size() != vetor.length) {
            throw new IllegalArgumentException(
                    "Vetor recebido precisa ter " + vetor.length + " posicoes, mas veio " + vetorRecebido);
        }
        for (int i = 0; i < vetor.length; i++) {
            vetor[i] = Math.max(vetor[i], vetorRecebido.get(i));
        }
        vetor[idAgencia] += 1;
        return copia();
    }

    /** Estado atual, sem contar como evento (nao incrementa nada). */
    public synchronized List<Long> valorAtual() {
        return copia();
    }

    private List<Long> copia() {
        List<Long> lista = new ArrayList<>(vetor.length);
        for (long posicao : vetor) {
            lista.add(posicao);
        }
        return List.copyOf(lista);
    }
}
