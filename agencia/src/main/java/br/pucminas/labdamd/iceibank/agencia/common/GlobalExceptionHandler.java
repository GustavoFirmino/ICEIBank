/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte C (API REST/MVC de contas)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.common;

import br.pucminas.labdamd.iceibank.agencia.common.exceptions.AgenciaDestinoIndisponivelException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaDuplicadaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ContaNaoEncontradaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.DadosInvalidosException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ParticaoInvalidaException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.SaldoInsuficienteException;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ValorInvalidoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Mapeia excecoes de dominio para respostas HTTP com o status correto. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ContaNaoEncontradaException.class)
    public ResponseEntity<ErroResponse> tratarContaNaoEncontrada(ContaNaoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErroResponse(ex.getMessage()));
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<ErroResponse> tratarSaldoInsuficiente(SaldoInsuficienteException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroResponse(ex.getMessage()));
    }

    @ExceptionHandler(ParticaoInvalidaException.class)
    public ResponseEntity<ErroResponse> tratarParticaoInvalida(ParticaoInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroResponse(ex.getMessage()));
    }

    @ExceptionHandler(ContaDuplicadaException.class)
    public ResponseEntity<ErroResponse> tratarContaDuplicada(ContaDuplicadaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroResponse(ex.getMessage()));
    }

    @ExceptionHandler(AgenciaDestinoIndisponivelException.class)
    public ResponseEntity<ErroResponse> tratarAgenciaDestinoIndisponivel(AgenciaDestinoIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErroResponse(ex.getMessage()));
    }

    @ExceptionHandler(ValorInvalidoException.class)
    public ResponseEntity<ErroResponse> tratarValorInvalido(ValorInvalidoException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroResponse(ex.getMessage()));
    }

    @ExceptionHandler(DadosInvalidosException.class)
    public ResponseEntity<ErroResponse> tratarDadosInvalidos(DadosInvalidosException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroResponse(ex.getMessage()));
    }
}
