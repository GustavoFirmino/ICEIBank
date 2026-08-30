/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte F (Autenticacao JWT)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.auth;

import br.pucminas.labdamd.iceibank.agencia.config.AppProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cadastro de usuarios de aplicativo em memoria, semeado a partir dos
 * usuarios de demonstracao do application.yml. As senhas nunca ficam em
 * texto puro depois de carregadas - sao hasheadas com BCrypt na inicializacao.
 */
@Component
public class UsuarioStore {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, Usuario> usuarios = new HashMap<>();

    public UsuarioStore(AppProperties appProperties) {
        for (AppProperties.UsuarioDemo demo : appProperties.usuarios()) {
            usuarios.put(demo.username(), new Usuario(demo.username(), passwordEncoder.encode(demo.password())));
        }
    }

    public boolean autenticar(String username, String senha) {
        return Optional.ofNullable(usuarios.get(username))
                .map(usuario -> passwordEncoder.matches(senha, usuario.passwordHash()))
                .orElse(false);
    }

    public boolean existe(String username) {
        return usuarios.containsKey(username);
    }
}
