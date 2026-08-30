/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Preparacao do frontend (Parte G)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 */
package br.pucminas.labdamd.iceibank.agencia.designsystem.dto;

import java.util.List;

public record DesignSystemResponse(
        String resumo,
        List<CorPaleta> paletaDeCores,
        String proporcaoRecomendada,
        TipografiaInfo tipografia,
        List<String> principiosDeUx,
        AcessibilidadeInfo acessibilidade,
        List<FonteDePesquisa> fontesDaPesquisa
) {
}
