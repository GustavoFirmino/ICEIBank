/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Funcionalidade adicional (idempotencia de transferencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 *
 * Guarda, por idOperacao, a resposta da PRIMEIRA vez que aquela operacao foi
 * processada com sucesso. Se a mesma requisicao (mesmo idOperacao) chegar de
 * novo - por exemplo, o cliente reenviando por timeout de rede sem saber se
 * a primeira tentativa deu certo - a operacao nao e reaplicada: devolvemos
 * a mesma resposta da primeira vez, com repetida=true.
 *
 * So precisa existir na agencia de ORIGEM (onde POST /transferencias e
 * chamado) - a agencia de destino nao tem esse conceito, ela so aplica um
 * credito remoto que ja chega calculado.
 *
 * Sem TTL de proposito: o estado inteiro da agencia e em memoria e some no
 * restart do processo, entao um mapa que "vive" enquanto o processo vive
 * ja e consistente com o resto do sprint - um TTL so adicionaria
 * complexidade sem beneficio real para este escopo.
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia;

import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.TransferenciaResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IdempotencyStore {

    private final Map<String, TransferenciaResponse> respostasPorOperacao = new ConcurrentHashMap<>();

    public TransferenciaResponse buscar(String idOperacao) {
        return respostasPorOperacao.get(idOperacao);
    }

    public void registrar(String idOperacao, TransferenciaResponse resposta) {
        respostasPorOperacao.put(idOperacao, resposta);
    }
}
