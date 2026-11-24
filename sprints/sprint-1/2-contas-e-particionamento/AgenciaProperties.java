/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Parte A (Modelagem e particao de contas)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.config;

import br.pucminas.labdamd.iceibank.agencia.common.exceptions.ParticaoInvalidaException;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao de particionamento entre agencias.
 *
 * Cada conta pertence a exatamente UMA agencia (particao, nao replicacao):
 * dado o id da conta, a agencia responsavel e id % totalAgencias.
 *
 * "id" e "internalKey" ficam nos arquivos application-agenciaN.yml / application.yml
 * (ver README) - "id" e diferente por instancia, o resto e compartilhado entre as 3.
 */
@ConfigurationProperties(prefix = "agencia")
public record AgenciaProperties(int id, int totalAgencias, int portaBase, String internalKey) {

    /**
     * Regra de particionamento: id_conta % numero_de_agencias.
     */
    public int agenciaResponsavel(long idConta) {
        return (int) (idConta % totalAgencias);
    }

    public boolean pertenceAEstaAgencia(long idConta) {
        return agenciaResponsavel(idConta) == id;
    }

    /** Lanca ParticaoInvalidaException se a conta nao pertencer a esta agencia. */
    public void validarParticaoOuLancar(long idConta) {
        if (!pertenceAEstaAgencia(idConta)) {
            throw new ParticaoInvalidaException(idConta, agenciaResponsavel(idConta));
        }
    }

    /**
     * URL base de outra agencia, calculada a partir da porta-base + id dela
     * (mesma convencao de portas usada em todos os roteiros: PORTA_BASE + OFFSET).
     */
    public String urlDaAgencia(int idAgencia) {
        return "http://localhost:" + (portaBase + idAgencia);
    }
}
