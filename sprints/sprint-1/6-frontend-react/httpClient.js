// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Unico lugar do frontend que sabe montar uma requisicao para a API: anexa
// o token (quando existe) e normaliza qualquer erro HTTP num AppError com
// o texto que a API mandou no campo "erro" - e a resposta direta a pergunta
// 12.3.1 do roteiro ("como o frontend lembra de reenviar o token").
import { AppError } from './AppError'

export async function apiFetch(baseUrl, caminho, { method = 'GET', body, token } = {}) {
  const cabecalhos = { 'Content-Type': 'application/json' }
  if (token) {
    cabecalhos.Authorization = `Bearer ${token}`
  }

  let resposta
  try {
    resposta = await fetch(`${baseUrl}${caminho}`, {
      method,
      headers: cabecalhos,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch (erroDeRede) {
    throw new AppError(0, 'Não foi possível conectar à agência selecionada. Ela está no ar?')
  }

  const textoCru = await resposta.text()
  const corpo = textoCru ? JSON.parse(textoCru) : null

  if (!resposta.ok) {
    const mensagem = corpo?.erro || `Erro inesperado (HTTP ${resposta.status}).`
    throw new AppError(resposta.status, mensagem)
  }

  return corpo
}
