/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia;

import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ComunicacaoAgenciaException;
import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.CreditarRemotoRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Implementacao real de RemoteBranchClient, via Spring RestClient sobre HTTP. */
@Component
public class RemoteBranchClientHttp implements RemoteBranchClient {

    // Sem timeout, uma agencia de destino "viva mas travada" (nao apenas fora
    // do ar) prenderia a thread que atende a requisicao indefinidamente.
    private static final int TIMEOUT_MS = 3000;

    private final AgenciaProperties agenciaProperties;
    private final RestClient restClient;

    public RemoteBranchClientHttp(AgenciaProperties agenciaProperties) {
        this.agenciaProperties = agenciaProperties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);

        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public void creditarRemoto(int idAgenciaDestino, long idConta, long valor, long timestampLamport, int origemAgencia) {
        String url = agenciaProperties.urlDaAgencia(idAgenciaDestino) + "/contas/" + idConta + "/creditar-remoto";

        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", agenciaProperties.internalKey())
                    .body(new CreditarRemotoRequest(valor, timestampLamport, origemAgencia))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException erro) {
            throw new ComunicacaoAgenciaException(
                    "Falha ao comunicar com a agencia " + idAgenciaDestino + ": " + erro.getMessage(), erro);
        }
    }
}
