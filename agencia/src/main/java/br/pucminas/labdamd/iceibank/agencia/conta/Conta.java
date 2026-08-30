/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte C (API REST/MVC de contas)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.conta;

import br.pucminas.labdamd.iceibank.agencia.common.exceptions.SaldoInsuficienteException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ValorInvalidoException;

/**
 * Conta bancaria, guardada em memoria (sem persistencia neste sprint - se o
 * processo da agencia for reiniciado, as contas somem, o que e esperado).
 *
 * saldo() / depositar() / sacar() sao synchronized: varias requisicoes podem
 * chegar concorrentemente para a MESMA conta (ex.: dois depositos ao mesmo
 * tempo), e o saldo e um estado mutavel compartilhado entre as threads que
 * atendem essas requisicoes.
 */
public class Conta {

    private final long id;
    private final String titular;
    private long saldo;

    public Conta(long id, String titular, long saldoInicial) {
        this.id = id;
        this.titular = titular;
        this.saldo = saldoInicial;
    }

    public long id() {
        return id;
    }

    public String titular() {
        return titular;
    }

    public synchronized long saldo() {
        return saldo;
    }

    public synchronized void depositar(long valor) {
        if (valor <= 0) {
            throw new ValorInvalidoException(valor);
        }
        saldo += valor;
    }

    public synchronized void sacar(long valor) {
        if (valor <= 0) {
            throw new ValorInvalidoException(valor);
        }
        if (saldo < valor) {
            throw new SaldoInsuficienteException(id);
        }
        saldo -= valor;
    }
}
