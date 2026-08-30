/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 *
 * Substitui a config temporaria (permitAll) das Partes C/D: agora as rotas
 * de conta exigem um JWT valido, e a rota interna entre agencias usa uma
 * chave separada (ver JwtAuthFilter e RESPOSTAS.md).
 */
package br.pucminas.labdamd.iceibank.agencia.config;

import br.pucminas.labdamd.iceibank.agencia.auth.JwtAuthFilter;
import br.pucminas.labdamd.iceibank.agencia.auth.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SecurityPaths.PUBLICAS.toArray(new String[0])).permitAll()
                        // /contas/*/creditar-remoto e protegida por X-Internal-Key, nao por JWT
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtService, objectMapper), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
