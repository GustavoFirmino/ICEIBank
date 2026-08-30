/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 *
 * Substitui a config temporaria (permitAll) das Partes C/D: agora as rotas
 * de conta exigem um JWT valido, e a rota interna entre agencias exige a
 * chave X-Internal-Key - ambas checadas por filtros dedicados (JwtAuthFilter
 * e InternalKeyAuthFilter), nao por codigo dentro dos controllers.
 */
package br.pucminas.labdamd.iceibank.agencia.config;

import br.pucminas.labdamd.iceibank.agencia.auth.InternalKeyAuthFilter;
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
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                            AgenciaProperties agenciaProperties, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SecurityPaths.LOGIN).permitAll()
                        // /contas/*/creditar-remoto e "publica" para o Spring Security (sem JWT),
                        // mas continua protegida - pelo InternalKeyAuthFilter abaixo, nao por JWT.
                        .requestMatchers(SecurityPaths.CREDITAR_REMOTO).permitAll()
                        // /design-system e so referencia de design (nao e dado de conta) - publica de proposito.
                        .requestMatchers(SecurityPaths.DESIGN_SYSTEM).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtService, objectMapper), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new InternalKeyAuthFilter(agenciaProperties, objectMapper), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
