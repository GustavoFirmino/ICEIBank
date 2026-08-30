/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.auth;

import br.pucminas.labdamd.iceibank.agencia.auth.dto.LoginRequest;
import br.pucminas.labdamd.iceibank.agencia.auth.dto.TokenResponse;
import br.pucminas.labdamd.iceibank.agencia.config.AppProperties;
import br.pucminas.labdamd.iceibank.agencia.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthControllerTest {

    private final JwtProperties jwtProperties = new JwtProperties(
            "segredo-de-teste-precisa-ter-pelo-menos-32-bytes-para-hs256", 300, true);
    private final JwtService jwtService = new JwtService(jwtProperties);
    private final UsuarioStore usuarioStore = new UsuarioStore(new AppProperties(
            List.of(new AppProperties.UsuarioDemo("gustavo", "senha123"))));
    private final AuthController controller = new AuthController(usuarioStore, jwtService, jwtProperties);

    @Test
    void loginComCredenciaisValidasRetornaToken() {
        ResponseEntity<?> resposta = controller.login(new LoginRequest("gustavo", "senha123"), null);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        TokenResponse corpo = (TokenResponse) resposta.getBody();
        assertNotNull(corpo);
        assertEquals("gustavo", jwtService.extrairUsername(corpo.token()));
        assertEquals(300, corpo.expiraEmSegundos());
    }

    @Test
    void loginComSenhaErradaRetorna401() {
        ResponseEntity<?> resposta = controller.login(new LoginRequest("gustavo", "senhaErrada"), null);
        assertEquals(HttpStatus.UNAUTHORIZED, resposta.getStatusCode());
    }

    @Test
    void loginComTtlOverrideGeraTokenComExpiracaoCustomizada() {
        ResponseEntity<?> resposta = controller.login(new LoginRequest("gustavo", "senha123"), 5L);

        TokenResponse corpo = (TokenResponse) resposta.getBody();
        assertNotNull(corpo);
        assertEquals(5, corpo.expiraEmSegundos());
    }

    @Test
    void ttlOverrideNaoConsegueUltrapassarOPadraoConfigurado() {
        // Correcao apos revisao de codigo: sem o clamp, ?ttlOverrideSeconds=999999999
        // geraria um token praticamente eterno, anulando a politica de expiracao curta.
        ResponseEntity<?> resposta = controller.login(new LoginRequest("gustavo", "senha123"), 999_999_999L);

        TokenResponse corpo = (TokenResponse) resposta.getBody();
        assertNotNull(corpo);
        assertEquals(300, corpo.expiraEmSegundos(), "o override so pode encurtar, nunca alongar, a expiracao padrao");
    }
}
