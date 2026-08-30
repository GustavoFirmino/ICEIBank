/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.auth;

import br.pucminas.labdamd.iceibank.agencia.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioStoreTest {

    private final UsuarioStore usuarioStore = new UsuarioStore(new AppProperties(
            List.of(new AppProperties.UsuarioDemo("gustavo", "senha123"))));

    @Test
    void autenticaComUsuarioESenhaCorretos() {
        assertTrue(usuarioStore.autenticar("gustavo", "senha123"));
    }

    @Test
    void naoAutenticaComSenhaErrada() {
        assertFalse(usuarioStore.autenticar("gustavo", "senhaErrada"));
    }

    @Test
    void naoAutenticaUsuarioInexistente() {
        assertFalse(usuarioStore.autenticar("naoexiste", "qualquer"));
    }

    @Test
    void existeIndicaCorretamenteSeOUsuarioEstaCadastrado() {
        assertTrue(usuarioStore.existe("gustavo"));
        assertFalse(usuarioStore.existe("fantasma"));
    }
}
