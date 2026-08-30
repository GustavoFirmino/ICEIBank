/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte G (CORS para o frontend)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 *
 * Expoe um bean CorsConfigurationSource - e ele (nao o WebMvcConfigurer.
 * addCorsMappings sozinho) que o Spring SECURITY usa quando registramos
 * .cors(...) em SecurityConfig. Sem esse bean, requisicoes com o header
 * Authorization (toda chamada autenticada do frontend) disparam um preflight
 * OPTIONS que a cadeia de filtros de seguranca rejeita ANTES de qualquer
 * header de CORS ser adicionado - o navegador entao reporta "bloqueado por
 * CORS", mesmo a origem estando liberada.
 */
package br.pucminas.labdamd.iceibank.agencia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class WebConfig {

    private final String origemPermitida;

    public WebConfig(Environment env) {
        this.origemPermitida = env.getProperty("cors.allowed-origin", "http://localhost:5173");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(List.of(origemPermitida));
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuracao);
        return source;
    }
}
