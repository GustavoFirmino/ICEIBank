/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte C (API REST/MVC de contas)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.conta;

import br.pucminas.labdamd.iceibank.agencia.conta.dto.ContaResponse;
import br.pucminas.labdamd.iceibank.agencia.conta.dto.CriarContaRequest;
import br.pucminas.labdamd.iceibank.agencia.conta.dto.MovimentoRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contas")
public class ContasController {

    private final ContaService contaService;

    public ContasController(ContaService contaService) {
        this.contaService = contaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContaResponse criarConta(@RequestBody CriarContaRequest request) {
        return contaService.criarConta(request);
    }

    @GetMapping("/{id}")
    public ContaResponse consultarSaldo(@PathVariable long id) {
        return contaService.consultarSaldo(id);
    }

    @PostMapping("/{id}/depositar")
    public ContaResponse depositar(@PathVariable long id, @RequestBody MovimentoRequest request) {
        return contaService.depositar(id, request.valor());
    }

    @PostMapping("/{id}/sacar")
    public ContaResponse sacar(@PathVariable long id, @RequestBody MovimentoRequest request) {
        return contaService.sacar(id, request.valor());
    }
}
