/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Preparacao do frontend (Parte G)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.designsystem;

import br.pucminas.labdamd.iceibank.agencia.designsystem.dto.DesignSystemResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DesignSystemControllerTest {

    private final DesignSystemController controller = new DesignSystemController();

    @Test
    void devolveUmaPaletaNaoVazia() {
        DesignSystemResponse resposta = controller.designSystem();
        assertFalse(resposta.paletaDeCores().isEmpty());
    }

    @Test
    void todasAsCoresTemHexValido() {
        DesignSystemResponse resposta = controller.designSystem();
        resposta.paletaDeCores().forEach(cor ->
                assertTrue(cor.hex().matches("^#[0-9A-Fa-f]{6}$"), "hex invalido: " + cor.hex()));
    }

    @Test
    void temPeloMenosUmaCorPorPapelEssencial() {
        DesignSystemResponse resposta = controller.designSystem();
        var papeis = resposta.paletaDeCores().stream().map(c -> c.papel()).toList();
        assertTrue(papeis.contains("sucesso"));
        assertTrue(papeis.contains("erro"));
        assertTrue(papeis.contains("atencao"));
    }

    @Test
    void temFontesDePesquisaCitadas() {
        DesignSystemResponse resposta = controller.designSystem();
        assertFalse(resposta.fontesDaPesquisa().isEmpty());
        resposta.fontesDaPesquisa().forEach(fonte -> assertTrue(fonte.url().startsWith("https://")));
    }

    @Test
    void temInformacaoDeAcessibilidadeWcag() {
        DesignSystemResponse resposta = controller.designSystem();
        assertTrue(resposta.acessibilidade().contrasteTextoMinimo().contains("4.5"));
    }
}
