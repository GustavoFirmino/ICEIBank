// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { apiFetch } from './httpClient'

// idOperacao (UUID) e gerado aqui, uma vez por clique do usuario - se o
// fetch falhar por rede e o React tentar de novo automaticamente, o backend
// ve o mesmo idOperacao e nao aplica a transferencia duas vezes (idempotencia).
export function transferir(baseUrl, token, idOrigem, idDestino, valor) {
  const idOperacao = crypto.randomUUID()
  return apiFetch(baseUrl, '/transferencias', {
    method: 'POST',
    token,
    body: { idOrigem: Number(idOrigem), idDestino: Number(idDestino), valor: Number(valor), idOperacao },
  })
}
