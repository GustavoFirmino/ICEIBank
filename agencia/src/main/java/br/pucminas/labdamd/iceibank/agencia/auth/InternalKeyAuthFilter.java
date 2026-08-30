/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 (correcao apos revisao de codigo)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 *
 * Antes desta correcao, a verificacao da chave interna entre agencias vivia
 * como um "if" manual dentro de TransferenciasController.creditarRemoto -
 * a rota estava marcada como permitAll() no Spring Security, e nada
 * estrutural garantia que aquele "if" continuaria ali se o controller fosse
 * refatorado. Este filtro centraliza essa checagem no mesmo nivel em que o
 * JwtAuthFilter protege as rotas de usuario, entao TODAS as rotas
 * protegidas (por JWT ou por chave interna) tem sua checagem imposta pela
 * cadeia de filtros do Spring Security, nao por codigo de controller.
 */
package br.pucminas.labdamd.iceibank.agencia.auth;

import br.pucminas.labdamd.iceibank.agencia.common.ErroResponse;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.config.SecurityPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class InternalKeyAuthFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final AgenciaProperties agenciaProperties;
    private final ObjectMapper objectMapper;

    public InternalKeyAuthFilter(AgenciaProperties agenciaProperties, ObjectMapper objectMapper) {
        this.agenciaProperties = agenciaProperties;
        this.objectMapper = objectMapper;
    }

    /** So se aplica a rota interna agencia-a-agencia - todas as outras passam direto. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PATH_MATCHER.match(SecurityPaths.CREDITAR_REMOTO, request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String chaveInterna = request.getHeader("X-Internal-Key");
        if (!agenciaProperties.internalKey().equals(chaveInterna)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    new ErroResponse("Chave interna entre agencias ausente ou invalida.")));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
