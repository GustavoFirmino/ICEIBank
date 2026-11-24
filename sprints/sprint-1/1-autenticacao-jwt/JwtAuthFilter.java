/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.auth;

import br.pucminas.labdamd.iceibank.agencia.common.ErroResponse;
import br.pucminas.labdamd.iceibank.agencia.config.SecurityPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Exige um JWT valido (Authorization: Bearer &lt;token&gt;) em todas as rotas
 * que leem/modificam contas. Duas rotas ficam de fora deste filtro:
 *  - POST /auth/login (obvio: e onde o token e obtido)
 *  - POST /contas/{id}/creditar-remoto (rota INTERNA agencia-a-agencia; usa
 *    o header X-Internal-Key, verificado dentro do proprio controller - ver
 *    justificativa dessa decisao de design na Parte F do RESPOSTAS.md)
 *
 * Requisicao sem token, com token invalido ou expirado -> 401, respondido
 * diretamente aqui (nao delega para o tratamento padrao do Spring Security,
 * que devolveria 403).
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Preflight de CORS (OPTIONS) nunca carrega Authorization - o navegador
        // manda essa checagem antes, sem nenhum header custom. Se este filtro
        // exigisse token nela, o preflight falharia e o navegador reportaria
        // erro de CORS mesmo com a origem liberada.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String caminho = request.getServletPath();
        return SecurityPaths.PUBLICAS.stream().anyMatch(padrao -> PATH_MATCHER.match(padrao, caminho));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String cabecalho = request.getHeader("Authorization");
        if (cabecalho == null || !cabecalho.startsWith("Bearer ")) {
            responderNaoAutorizado(response, "Token ausente. Envie Authorization: Bearer <token>.");
            return;
        }

        String token = cabecalho.substring("Bearer ".length());
        try {
            String username = jwtService.extrairUsername(token);
            var autenticacao = new UsernamePasswordAuthenticationToken(username, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(autenticacao);
            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            responderNaoAutorizado(response, "Token invalido ou expirado: " + e.getMessage());
        }
    }

    private void responderNaoAutorizado(HttpServletResponse response, String mensagem) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(new ErroResponse(mensagem)));
    }
}
