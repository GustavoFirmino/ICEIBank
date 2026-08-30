/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia;

import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.CreditarRemotoRequest;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.CreditarRemotoResponse;
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

    /**
     * Rota INTERNA, chamada agencia-a-agencia (nunca por um usuario/frontend).
     * Nao usa JWT: exige o header X-Internal-Key, compartilhado entre as 3
     * agencias. A checagem em si acontece antes de chegar aqui, no
     * InternalKeyAuthFilter (centralizado na cadeia de filtros do Spring
     * Security, nao como um "if" dentro deste controller - ver
     * SecurityConfig e a justificativa na Parte F do RESPOSTAS.md).
     */
    @PostMapping("/contas/{id}/creditar-remoto")
    public CreditarRemotoResponse creditarRemoto(@PathVariable long id, @RequestBody CreditarRemotoRequest request) {
        return transferenciaService.creditarRemoto(id, request.valor(), request.timestampLamport(), request.origemAgencia());
    }
}
