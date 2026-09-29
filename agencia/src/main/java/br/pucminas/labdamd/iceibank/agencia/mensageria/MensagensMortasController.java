/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Funcionalidade adicional (dead-letter queue com reprocessamento)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.mensageria;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Protegido por JWT como qualquer rota que nao seja login (ver SecurityConfig). */
@RestController
@RequestMapping("/mensagens-mortas")
public class MensagensMortasController {

    private final MensagensMortasService mensagensMortasService;

    public MensagensMortasController(MensagensMortasService mensagensMortasService) {
        this.mensagensMortasService = mensagensMortasService;
    }

    /** Creditos rejeitados (ex.: conta inexistente) cuja conta de destino e desta agencia. */
    @GetMapping
    public List<CreditoRemotoMensagem> listar() {
        return mensagensMortasService.listar();
    }

    /** Republica esses creditos; util depois que a conta de destino voltou a existir. */
    @PostMapping("/reprocessar")
    public MensagensMortasService.ResultadoReprocessamento reprocessar() {
        return mensagensMortasService.reprocessar();
    }
}
