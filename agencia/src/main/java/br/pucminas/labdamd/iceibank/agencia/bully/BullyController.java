/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 3 - Parte A (rotas do Bully)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.bully;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Rotas publicas (sem JWT) do Bully - ver SecurityPaths.BULLY. */
@RestController
@RequestMapping("/bully")
public class BullyController {

    public record EleicaoRequest(int origemId, List<Long> vetorEnvio) {
    }

    public record CoordenadorRequest(int liderId, List<Long> vetorEnvio) {
    }

    private final BullyService bully;

    public BullyController(BullyService bully) {
        this.bully = bully;
    }

    @PostMapping("/eleicao")
    public ResponseEntity<Void> eleicao(@RequestBody EleicaoRequest request) {
        bully.receberEleicao(request.origemId(), request.vetorEnvio());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/coordenador")
    public ResponseEntity<Void> coordenador(@RequestBody CoordenadorRequest request) {
        bully.receberCoordenador(request.liderId(), request.vetorEnvio());
        return ResponseEntity.ok().build();
    }

    /** {"idAgencia": 1, "liderAtual": 2} - liderAtual e null enquanto nao houver lider conhecida. */
    @GetMapping("/lider")
    public Map<String, Object> lider() {
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("idAgencia", bully.idAgencia());
        resposta.put("liderAtual", bully.obterLiderAtual());
        return resposta;
    }
}
