/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Funcionalidade adicional (dead-letter queue com reprocessamento)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.mensageria;

import br.pucminas.labdamd.iceibank.agencia.clock.RelogioVetorialService;
import br.pucminas.labdamd.iceibank.agencia.common.exceptions.MensageriaIndisponivelException;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.eventlog.EventLogService;
import br.pucminas.labdamd.iceibank.agencia.eventlog.TipoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Funcionalidade adicional: fila de mensagens NAO processadas (dead-letter) com inspecao e
 * reprocessamento.
 *
 * Por que existe: no cenario da Parte C (a agencia de destino reiniciou e perdeu as contas em
 * memoria), o credito nao pode ser aplicado. Sem dead-letter, ou a mensagem seria confirmada e o
 * dinheiro sumiria em silencio, ou seria reentregue em loop. Com ela, a mensagem fica RETIDA com
 * todos os dados; quando a conta voltar a existir, o operador reprocessa e o credito e aplicado.
 *
 * Cada agencia so enxerga e so reprocessa as mensagens cuja conta de DESTINO e dela.
 */
@Service
public class MensagensMortasService {

    private static final Logger log = LoggerFactory.getLogger(MensagensMortasService.class);
    private static final int LIMITE_POR_CHAMADA = 500;

    /** Resultado de um reprocessamento. */
    public record ResultadoReprocessamento(int reprocessadas, int pendentes) {
    }

    private final AgenciaProperties agenciaProperties;
    private final FilaDeMensagensMortas filaDeMensagensMortas;
    private final PublicadorCreditos publicadorCreditos;
    private final EventLogService eventLog;
    private final RelogioVetorialService relogio;

    public MensagensMortasService(AgenciaProperties agenciaProperties, FilaDeMensagensMortas filaDeMensagensMortas,
                                  PublicadorCreditos publicadorCreditos, EventLogService eventLog,
                                  RelogioVetorialService relogio) {
        this.agenciaProperties = agenciaProperties;
        this.filaDeMensagensMortas = filaDeMensagensMortas;
        this.publicadorCreditos = publicadorCreditos;
        this.eventLog = eventLog;
        this.relogio = relogio;
    }

    /** Lista (sem remover nada) as mensagens mortas cuja conta de destino pertence a esta agencia. */
    public List<CreditoRemotoMensagem> listar() {
        List<CreditoRemotoMensagem> mensagens = new ArrayList<>();
        filaDeMensagensMortas.percorrer(LIMITE_POR_CHAMADA, mensagem -> {
            if (destinadaAEstaAgencia(mensagem)) {
                mensagens.add(mensagem);
            }
            return FilaDeMensagensMortas.Decisao.DEVOLVER;
        });
        return mensagens;
    }

    /**
     * Republica na exchange, uma a uma, as mensagens mortas desta agencia. So tira a mensagem da
     * dead-letter queue DEPOIS de o broker confirmar a nova publicacao - se a republicacao falhar,
     * ela continua la. Se a conta AINDA nao existir, o consumidor a rejeita de novo e ela volta
     * para a dead-letter queue: reprocessar e seguro de repetir.
     */
    public ResultadoReprocessamento reprocessar() {
        int[] contadores = new int[2]; // [0] = reprocessadas, [1] = pendentes (destinadas aqui e nao republicadas)
        filaDeMensagensMortas.percorrer(LIMITE_POR_CHAMADA, mensagem -> {
            if (!destinadaAEstaAgencia(mensagem)) {
                return FilaDeMensagensMortas.Decisao.DEVOLVER;
            }
            // O vetor do evento e tomado ANTES de publicar: o consumidor roda em outra thread e pode
            // aplicar o credito antes mesmo desta thread voltar de publicar(). Com o vetor anterior,
            // o reprocessamento fica causalmente ANTES do credito, como deve ser.
            List<Long> vetorReprocessamento = relogio.eventoLocal();
            Instant instanteDoReprocessamento = Instant.now();
            try {
                publicadorCreditos.publicar(agenciaProperties.agenciaResponsavel(mensagem.idConta()), mensagem);
            } catch (MensageriaIndisponivelException erro) {
                log.warn("Nao foi possivel republicar a mensagem {}: {}", mensagem.idMensagem(), erro.getMessage());
                contadores[1]++;
                return FilaDeMensagensMortas.Decisao.DEVOLVER;
            }
            eventLog.registrar(TipoEvento.CREDITO_REMOTO_REPROCESSADO, vetorReprocessamento, mensagem.idConta(),
                    Map.of("idMensagem", mensagem.idMensagem(), "valor", mensagem.valor(),
                            "origemAgencia", mensagem.origemAgencia()), instanteDoReprocessamento);
            contadores[0]++;
            return FilaDeMensagensMortas.Decisao.CONFIRMAR;
        });
        return new ResultadoReprocessamento(contadores[0], contadores[1]);
    }

    private boolean destinadaAEstaAgencia(CreditoRemotoMensagem mensagem) {
        return agenciaProperties.pertenceAEstaAgencia(mensagem.idConta());
    }
}
