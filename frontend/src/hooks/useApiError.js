// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Traduz um AppError (ou qualquer erro) numa mensagem pronta para mostrar
// na tela - e distingue 401 (token ausente/expirado, desloga automaticamente
// e avisa a pessoa usuaria) de erros de dominio normais (saldo insuficiente,
// conta nao encontrada, falha de comunicacao entre agencias) - resposta
// direta a pergunta 12.3.2 do roteiro.
import { useAuth } from './useAuth'

export function useApiError() {
  const { logout } = useAuth()

  function tratar(erro) {
    if (erro?.status === 401) {
      logout()
      return 'Sua sessão expirou ou o token é inválido. Faça login novamente.'
    }
    if (erro?.status === 0) {
      return erro.message
    }
    if (erro?.status) {
      return erro.message || 'Ocorreu um erro ao processar sua solicitação.'
    }
    return 'Erro inesperado. Tente novamente.'
  }

  return { tratar }
}
