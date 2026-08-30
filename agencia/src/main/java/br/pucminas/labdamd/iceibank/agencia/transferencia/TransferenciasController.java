/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia;

import br.pucminas.labdamd.iceibank.agencia.common.ErroResponse;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.CreditarRemotoRequest;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.TransferenciaRequest;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.TransferenciaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class TransferenciasController {

    private final TransferenciaService transferenciaService;
    private final AgenciaProperties agenciaProperties;

    public TransferenciasController(TransferenciaService transferenciaService, AgenciaProperties agenciaProperties) {
        this.transferenciaService = transferenciaService;
        this.agenciaProperties = agenciaProperties;
    }

    @PostMapping("/transferencias")
    public TransferenciaResponse transferir(@RequestBody TransferenciaRequest request) {
        return transferenciaService.transferir(request);
    }

    /**
     * Rota INTERNA, chamada agencia-a-agencia (nunca por um usuario/frontend).
     * Nao usa JWT: exige o header X-Internal-Key, compartilhado entre as 3
     * agencias (ver application.yml e RESPOSTAS.md - justificativa na Parte F).
     */
    @PostMapping("/contas/{id}/creditar-remoto")
    public ResponseEntity<?> creditarRemoto(@PathVariable long id,
                                             @RequestBody CreditarRemotoRequest request,
                                             @RequestHeader(value = "X-Internal-Key", required = false) String chaveInterna) {
        if (!agenciaProperties.internalKey().equals(chaveInterna)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErroResponse("Chave interna entre agencias ausente ou invalida."));
        }
        return ResponseEntity.ok(transferenciaService.creditarRemoto(
                id, request.valor(), request.timestampLamport(), request.origemAgencia()));
    }
}
