// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { apiFetch } from './httpClient'

// Rota publica (nao exige token) - usada para o app se "pintar" com a
// paleta pesquisada em GET /design-system, sem precisar login antes.
export function buscarDesignSystem(baseUrl) {
  return apiFetch(baseUrl, '/design-system')
}
