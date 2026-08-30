/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.auth;

import br.pucminas.labdamd.iceibank.agencia.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private final JwtProperties properties = new JwtProperties(
            "segredo-de-teste-precisa-ter-pelo-menos-32-bytes-para-hs256", 300, true);
    private final JwtService jwtService = new JwtService(properties);

    @Test
    void tokenGeradoTrazOUsernameCorretoNaClaimSub() {
        String token = jwtService.gerarToken("gustavo");
        assertEquals("gustavo", jwtService.extrairUsername(token));
    }

    @Test
    void tokenComTtlNegativoJaNasceExpirado() throws InterruptedException {
        String token = jwtService.gerarToken("gustavo", -1);
        // precisa ter passado do "exp" no momento da validacao
        assertThrows(ExpiredJwtException.class, () -> jwtService.validarToken(token));
    }

    @Test
    void tokenComAssinaturaDeOutroSegredoEInvalido() {
        String token = jwtService.gerarToken("gustavo");
        JwtProperties outroSegredo = new JwtProperties(
                "outro-segredo-completamente-diferente-tambem-com-32-bytes", 300, true);
        JwtService outroServico = new JwtService(outroSegredo);

        assertThrows(JwtException.class, () -> outroServico.validarToken(token));
    }

    @Test
    void tokenMalformadoLancaExcecao() {
        assertThrows(JwtException.class, () -> jwtService.validarToken("isto-nao-e-um-jwt"));
    }
}
