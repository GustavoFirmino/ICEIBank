/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte D (Transferencias)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.transferencia;

import br.pucminas.labdamd.iceibank.agencia.config.AgenciaProperties;
import br.pucminas.labdamd.iceibank.agencia.transferencia.dto.CreditarRemotoRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Implementacao real de RemoteBranchClient, via Spring RestClient sobre HTTP. */
@Component
public class RemoteBranchClientHttp implements RemoteBranchClient {

    private final AgenciaProperties agenciaProperties;
    private final RestClient restClient;

    public RemoteBranchClientHttp(AgenciaProperties agenciaProperties) {
        this.agenciaProperties = agenciaProperties;
        this.restClient = RestClient.create();
    }

    @Override
    public void creditarRemoto(int idAgenciaDestino, long idConta, long valor, long timestampLamport, int origemAgencia) {
        String url = agenciaProperties.urlDaAgencia(idAgenciaDestino) + "/contas/" + idConta + "/creditar-remoto";

        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Key", agenciaProperties.internalKey())
                .body(new CreditarRemotoRequest(valor, timestampLamport, origemAgencia))
                .retrieve()
                .toBodilessEntity();
    }
}
