/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 2 - Parte C (Publish/Subscribe entre agencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.mensageria;

/**
 * Publica um credito remoto na exchange do RabbitMQ. E uma interface (como era
 * o RemoteBranchClient do Sprint 1) para que TransferenciaService possa ser
 * testado com um duble simples, sem broker e sem framework de mock.
 *
 * Contrato: {@link #publicar} so retorna normalmente DEPOIS que o broker
 * confirmou que aceitou a mensagem (publisher confirm). Se o broker estiver
 * fora do ar, recusar ou nao confirmar a tempo, lanca
 * {@link br.pucminas.labdamd.iceibank.agencia.common.exceptions.MensageriaIndisponivelException}
 * - e so nesse caso o chamador pode estornar o debito com seguranca.
 */
public interface PublicadorCreditos {

    void publicar(int idAgenciaDestino, CreditoRemotoMensagem mensagem);
}
