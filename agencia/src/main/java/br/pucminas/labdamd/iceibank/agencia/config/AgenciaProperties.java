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
 * "id" fica em application-agenciaN.yml (diferente por instancia); o resto e compartilhado
 * entre as 3 (application.yml). No Sprint 2 nao existe mais chave interna nem URL de outra
 * agencia: agencias nao se chamam por HTTP, so trocam mensagens pelo RabbitMQ.
 */
@ConfigurationProperties(prefix = "agencia")
public record AgenciaProperties(int id, int totalAgencias, int portaBase) {

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
}
