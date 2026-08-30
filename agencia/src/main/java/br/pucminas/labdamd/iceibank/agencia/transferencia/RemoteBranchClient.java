/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia;

/**
 * Chamada REST direta entre agencias (Parte D) - a agencia de origem
 * contata diretamente a agencia de destino para creditar a conta remota.
 *
 * E uma interface (em vez de classe concreta) para que TransferenciaService
 * possa ser testado com um dublê de teste simples, sem precisar de um
 * servidor HTTP real nem de bibliotecas de mock em cima de classes finais.
 */
public interface RemoteBranchClient {

    /**
     * Chama POST /contas/{id}/creditar-remoto na agencia de destino.
     * Lanca uma excecao em runtime se a chamada falhar por qualquer motivo
     * (agencia fora do ar, timeout, resposta de erro) - quem chama decide o
     * que fazer com a falha (ver TransferenciaService).
     */
    void creditarRemoto(int idAgenciaDestino, long idConta, long valor, long timestampLamport, int origemAgencia);
}
