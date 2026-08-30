/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.auth;

import br.pucminas.labdamd.iceibank.agencia.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Emissao e validacao de tokens JWT. Claims usadas: "sub" (username), "iat"
 * e "exp" - de proposito, SEM claim de conta/permissao (ver Parte F do
 * RESPOSTAS.md: este sprint autentica "alguem esta logado", mas nao
 * restringe quais contas essa pessoa pode operar).
 */
@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey chave;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.chave = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** Gera um token para o usuario, com expiracao padrao (jwt.expiration-seconds). */
    public String gerarToken(String username) {
        return gerarToken(username, jwtProperties.expirationSeconds());
    }

    /**
     * Gera um token com uma duracao customizada, em segundos.
     * Usado pelo endpoint de debug /auth/login?ttlOverrideSeconds=N para
     * conseguir demonstrar um token ja expirado sem esperar os 5 minutos
     * padrao (ver AuthController e jwt.debug-endpoints-enabled).
     */
    public String gerarToken(String username, long ttlSegundos) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plusSeconds(ttlSegundos)))
                .signWith(chave)
                .compact();
    }

    /** Valida o token e devolve as claims. Lanca JwtException (expirado, assinatura invalida, etc.) se invalido. */
    public Claims validarToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extrairUsername(String token) throws JwtException {
        return validarToken(token).getSubject();
    }
}
