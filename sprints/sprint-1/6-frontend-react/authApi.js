// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { apiFetch } from './httpClient'

export function login(baseUrl, username, password) {
  return apiFetch(baseUrl, '/auth/login', { method: 'POST', body: { username, password } })
}
