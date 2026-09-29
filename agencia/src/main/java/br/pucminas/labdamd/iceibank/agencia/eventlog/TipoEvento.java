/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 (registro de eventos) / Sprint 2 (eventos da mensageria)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.eventlog;

public enum TipoEvento {
    CRIACAO_CONTA,
    DEPOSITO,
    SAQUE,
    TRANSFERENCIA_DEBITO,
    TRANSFERENCIA_CREDITO,
    /** Sprint 2: a agencia de ORIGEM publicou o credito no RabbitMQ (regra 2 do relogio vetorial: ao enviar). */
    TRANSFERENCIA_PUBLICADA,
    /** A agencia de DESTINO consumiu a mensagem e creditou a conta (regra 3: ao receber). */
    TRANSFERENCIA_CREDITO_REMOTO,
    /** Debito desfeito porque a transferencia nao pode ser concluida (conta local inexistente, ou broker fora do ar). */
    TRANSFERENCIA_REVERTIDA,
    /** Sprint 2 (dead-letter): mensagem recebida mas NAO aplicavel (ex.: conta inexistente); foi para a dead-letter queue. */
    CREDITO_REMOTO_FALHOU,
    /** Sprint 2: a mesma mensagem (mesmo idMensagem) chegou de novo e foi ignorada - o credito NAO foi aplicado duas vezes. */
    CREDITO_REMOTO_DUPLICADO,
    /** Funcionalidade adicional: um credito que estava na dead-letter queue foi republicado para nova tentativa. */
    CREDITO_REMOTO_REPROCESSADO,
    LOGIN
}
