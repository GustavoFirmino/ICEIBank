/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Funcionalidade adicional (idempotencia de transferencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 *
 * Garante que uma operacao identificada por idOperacao seja executada NO
 * MAXIMO UMA VEZ, mesmo se:
 *  a) duas requisicoes com o MESMO idOperacao chegarem concorrentemente
 *     (corrida de threads) - resolvido com um lock por idOperacao, nao um
 *     simples "buscar, depois registrar" (que teria uma janela de corrida
 *     entre as duas operacoes nao-atomicas);
 *  b) a primeira tentativa tiver FALHADO (ex.: a falha conhecida da Parte D,
 *     onde o debito local ja foi aplicado antes da chamada remota falhar) -
 *     tambem cacheamos a FALHA, nao so o sucesso. Sem isso, um cliente que
 *     reenvia exatamente por causa dessa falha conhecida acabaria debitando
 *     a conta de novo a cada tentativa - o oposto do que idempotencia deveria
 *     garantir. O retry recebe o mesmo erro 502 de antes, sem reexecutar nada.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class IdempotencyStore {

    // Um lock por idOperacao - so processos que operam no MESMO idOperacao
    // disputam o mesmo lock; operacoes diferentes nunca se bloqueiam entre si.
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TransferenciaResponse> sucessos = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RuntimeException> falhas = new ConcurrentHashMap<>();

    /**
     * Executa "operacao" no maximo uma vez para este idOperacao. Chamadas
     * subsequentes (concorrentes ou depois no tempo) com o mesmo idOperacao
     * NUNCA reexecutam - devolvem o mesmo sucesso (com repetida=true) ou
     * relancam a mesma falha da primeira tentativa.
     */
    public TransferenciaResponse executarUmaVezSo(String idOperacao, Supplier<TransferenciaResponse> operacao) {
        Object lock = locks.computeIfAbsent(idOperacao, chave -> new Object());

        synchronized (lock) {
            TransferenciaResponse sucessoAnterior = sucessos.get(idOperacao);
            if (sucessoAnterior != null) {
                return new TransferenciaResponse(sucessoAnterior.mensagem(), true);
            }

            RuntimeException falhaAnterior = falhas.get(idOperacao);
            if (falhaAnterior != null) {
                throw falhaAnterior;
            }

            try {
                TransferenciaResponse resposta = operacao.get();
                sucessos.put(idOperacao, resposta);
                return resposta;
            } catch (RuntimeException erro) {
                falhas.put(idOperacao, erro);
                throw erro;
            }
        }
    }
}
