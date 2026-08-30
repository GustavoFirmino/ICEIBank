/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.auth;

import br.pucminas.labdamd.iceibank.agencia.config.JwtProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthFilterTest {

    private final JwtProperties properties = new JwtProperties(
            "segredo-de-teste-precisa-ter-pelo-menos-32-bytes-para-hs256", 300, true);
    private final JwtService jwtService = new JwtService(properties);
    private final JwtAuthFilter filtro = new JwtAuthFilter(jwtService, new ObjectMapper());

    @AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void naoFiltraRotaDeLogin() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");
        assertTrue(invocarShouldNotFilter(request));
    }

    @Test
    void naoFiltraRotaInternaDeCreditoRemoto() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/contas/7/creditar-remoto");
        request.setServletPath("/contas/7/creditar-remoto");
        assertTrue(invocarShouldNotFilter(request));
    }

    @Test
    void filtraRotasNormaisDeConta() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/contas/7");
        request.setServletPath("/contas/7");
        assertFalse(invocarShouldNotFilter(request));
    }

    @Test
    void semTokenRespondeQuatroZeroUmENaoChamaOResto() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/contas/7");
        request.setServletPath("/contas/7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filtro.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest(), "a chain nao deveria ter sido chamada");
    }

    @Test
    void comTokenValidoChamaOResto() throws ServletException, IOException {
        String token = jwtService.gerarToken("gustavo");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/contas/7");
        request.setServletPath("/contas/7");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filtro.doFilter(request, response, chain);

        assertNotNull(chain.getRequest(), "a chain deveria ter sido chamada");
        assertEquals("gustavo", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void comTokenExpiradoRespondeQuatroZeroUm() throws ServletException, IOException {
        String tokenExpirado = jwtService.gerarToken("gustavo", -10);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/contas/7");
        request.setServletPath("/contas/7");
        request.addHeader("Authorization", "Bearer " + tokenExpirado);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filtro.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
        assertTrue(response.getContentAsString().contains("expirado") || response.getContentAsString().toLowerCase().contains("expired"));
    }

    private boolean invocarShouldNotFilter(MockHttpServletRequest request) {
        try {
            var metodo = JwtAuthFilter.class.getDeclaredMethod("shouldNotFilter", jakarta.servlet.http.HttpServletRequest.class);
            metodo.setAccessible(true);
            return (boolean) metodo.invoke(filtro, request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
