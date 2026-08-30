/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte C (API REST/MVC de contas)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.conta;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Repositorio em memoria (Sprint 1 nao usa banco de dados - ver README). */
@Repository
public class ContaRepository {

    private final Map<Long, Conta> contas = new ConcurrentHashMap<>();

    public Optional<Conta> buscar(long id) {
        return Optional.ofNullable(contas.get(id));
    }

    public boolean existe(long id) {
        return contas.containsKey(id);
    }

    /**
     * Salva a conta somente se o id ainda nao existir - operacao atomica
     * (ConcurrentHashMap#putIfAbsent) para evitar que duas requisicoes de
     * criacao simultaneas para o mesmo id "vencam a corrida" e uma delas
     * sobrescreva silenciosamente a outra.
     */
    public boolean salvarSeNaoExiste(Conta conta) {
        return contas.putIfAbsent(conta.id(), conta) == null;
    }
}
