// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { apiFetch } from './httpClient'

export function consultarConta(baseUrl, token, id) {
  return apiFetch(baseUrl, `/contas/${id}`, { token })
}

export function depositar(baseUrl, token, id, valor) {
  return apiFetch(baseUrl, `/contas/${id}/depositar`, { method: 'POST', token, body: { valor } })
}

export function sacar(baseUrl, token, id, valor) {
  return apiFetch(baseUrl, `/contas/${id}/sacar`, { method: 'POST', token, body: { valor } })
}

export function historico(baseUrl, token, id) {
  return apiFetch(baseUrl, `/contas/${id}/historico`, { token })
}
