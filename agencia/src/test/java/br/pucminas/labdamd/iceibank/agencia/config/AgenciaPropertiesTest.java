/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte A (Modelagem e particao de contas)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgenciaPropertiesTest {

    private final AgenciaProperties agencia0 = new AgenciaProperties(0, 3, 4047, "chave");
    private final AgenciaProperties agencia1 = new AgenciaProperties(1, 3, 4047, "chave");

    @Test
    void contaZeroPertenceAAgenciaZero() {
        assertEquals(0, agencia0.agenciaResponsavel(0));
        assertTrue(agencia0.pertenceAEstaAgencia(0));
    }

    @Test
    void contaTresVoltaParaAgenciaZero() {
        assertEquals(0, agencia0.agenciaResponsavel(3));
        assertEquals(1, agencia0.agenciaResponsavel(4));
        assertEquals(2, agencia0.agenciaResponsavel(5));
    }

    @Test
    void contaDeOutraAgenciaNaoPertenceAEstaAgencia() {
        assertFalse(agencia0.pertenceAEstaAgencia(1));
        assertTrue(agencia1.pertenceAEstaAgencia(1));
    }

    @Test
    void urlDaAgenciaUsaPortaBaseMaisId() {
        assertEquals("http://localhost:4047", agencia0.urlDaAgencia(0));
        assertEquals("http://localhost:4048", agencia0.urlDaAgencia(1));
        assertEquals("http://localhost:4049", agencia0.urlDaAgencia(2));
    }
}
