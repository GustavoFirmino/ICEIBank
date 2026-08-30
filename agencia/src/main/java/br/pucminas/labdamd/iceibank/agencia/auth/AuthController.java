/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.auth;

import br.pucminas.labdamd.iceibank.agencia.auth.dto.LoginRequest;
import br.pucminas.labdamd.iceibank.agencia.auth.dto.TokenResponse;
import br.pucminas.labdamd.iceibank.agencia.common.ErroResponse;
import br.pucminas.labdamd.iceibank.agencia.config.JwtProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final UsuarioStore usuarioStore;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthController(UsuarioStore usuarioStore, JwtService jwtService, JwtProperties jwtProperties) {
        this.usuarioStore = usuarioStore;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * ttlOverrideSeconds e um parametro OPCIONAL, so para facilitar gerar um
     * token ja proximo de expirar/expirado na hora de capturar a evidencia
     * de "auth-token-expirado" (Parte F) - so funciona se
     * jwt.debug-endpoints-enabled=true (documentado no RESPOSTAS.md).
     */
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
                                    @RequestParam(required = false) Long ttlOverrideSeconds) {
        if (!usuarioStore.autenticar(request.username(), request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErroResponse("Usuario ou senha invalidos."));
        }

        long ttl = resolverTtl(ttlOverrideSeconds);
        String token = jwtService.gerarToken(request.username(), ttl);
        return ResponseEntity.ok(new TokenResponse(token, ttl));
    }

    /**
     * O override de debug so pode ENCURTAR a expiracao (nunca alonga-la
     * alem do padrao configurado) - senao qualquer cliente poderia pedir
     * um token praticamente eterno via ?ttlOverrideSeconds=999999999,
     * o que anularia a propria politica de expiracao curta do JWT.
     *
     * Nao ha limite inferior: valores zero ou negativos sao propositalmente
     * permitidos, pois so servem para gerar um token JA expirado (o cenario
     * de evidencia "auth-token-expirado" da Parte F) - nao ha risco de
     * seguranca em permitir isso, ja que so torna o token AINDA mais curto.
     */
    private long resolverTtl(Long ttlOverrideSeconds) {
        long padrao = jwtProperties.expirationSeconds();
        if (ttlOverrideSeconds == null || !jwtProperties.debugEndpointsEnabled()) {
            return padrao;
        }
        return Math.min(ttlOverrideSeconds, padrao);
    }
}
