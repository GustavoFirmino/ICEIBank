/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 *
 * As rotas de conta e de transferencia exigem um JWT valido, checado pelo
 * JwtAuthFilter (nao por codigo dentro dos controllers). No Sprint 1 havia
 * tambem a rota interna creditar-remoto, protegida por X-Internal-Key; no
 * Sprint 2 ela foi removida (o credito entre agencias chega por mensageria).
 */
package br.pucminas.labdamd.iceibank.agencia.config;

import br.pucminas.labdamd.iceibank.agencia.auth.JwtAuthFilter;
import br.pucminas.labdamd.iceibank.agencia.auth.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                            ObjectMapper objectMapper,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Preflight do CORS (OPTIONS) precisa passar SEM exigir JWT -
                        // o navegador nunca manda esses headers numa requisicao de preflight.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(SecurityPaths.LOGIN).permitAll()
                        // /design-system e so referencia de design (nao e dado de conta) - publica de proposito.
                        .requestMatchers(SecurityPaths.DESIGN_SYSTEM).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtService, objectMapper), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
