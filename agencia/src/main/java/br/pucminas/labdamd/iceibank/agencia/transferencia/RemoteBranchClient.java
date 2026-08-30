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
     *
     * Contrato de excecao: implementacoes DEVEM lancar especificamente
     * {@link br.pucminas.labdamd.iceibank.agencia.common.exceptions.ComunicacaoAgenciaException}
     * quando a falha for de COMUNICACAO com a agencia de destino (fora do
     * ar, timeout, resposta de erro HTTP). E esse tipo especifico que
     * TransferenciaService captura para aplicar a limitacao conhecida
     * (debito nao revertido) - lancar um RuntimeException generico faria a
     * falha propagar sem tratamento, e lancar ComunicacaoAgenciaException
     * para um bug que NAO e de comunicacao mascararia o bug real como se
     * fosse uma agencia fora do ar. Qualquer outra excecao (bug de
     * programacao) deve propagar normalmente, sem ser encapsulada.
     */
    void creditarRemoto(int idAgenciaDestino, long idConta, long valor, long timestampLamport, int origemAgencia);
}
