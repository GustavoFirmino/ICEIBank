/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia;

import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.TransferenciaRequest;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.TransferenciaResponse;
import org.springframework.web.bind.annotation.*;

@RestController
public class TransferenciasController {

    private final TransferenciaService transferenciaService;

    public TransferenciasController(TransferenciaService transferenciaService) {
        this.transferenciaService = transferenciaService;
    }

    @PostMapping("/transferencias")
    public TransferenciaResponse transferir(@RequestBody TransferenciaRequest request) {
        return transferenciaService.transferir(request);
    }
}
