// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Guarda o token e o nome do usuario logado. O token e persistido no
// localStorage: e assim que o frontend "lembra" de reenviar o token depois
// do login (mesmo se a pagina for recarregada) - resposta direta a
// pergunta 12.3.1 do roteiro.
import { createContext, useEffect, useState } from 'react'
import { login as loginNaApi } from '../api/authApi'
import { jwtEstaExpirado } from '../utils/jwt'

const CHAVE_TOKEN = 'iceibank.token'
const CHAVE_USERNAME = 'iceibank.username'

export const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(CHAVE_TOKEN))
  const [username, setUsername] = useState(() => localStorage.getItem(CHAVE_USERNAME))

  // Se o token guardado ja estiver expirado (ex.: usuario deixou a aba
  // aberta por mais de 5 minutos), desloga automaticamente ao abrir o app -
  // evita mandar requisicoes que a API vai rejeitar de qualquer forma.
  useEffect(() => {
    if (token && jwtEstaExpirado(token)) {
      logout()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function autenticar(baseUrl, usuario, senha) {
    const resposta = await loginNaApi(baseUrl, usuario, senha)
    localStorage.setItem(CHAVE_TOKEN, resposta.token)
    localStorage.setItem(CHAVE_USERNAME, usuario)
    setToken(resposta.token)
    setUsername(usuario)
  }

  function logout() {
    localStorage.removeItem(CHAVE_TOKEN)
    localStorage.removeItem(CHAVE_USERNAME)
    setToken(null)
    setUsername(null)
  }

  const value = { token, username, autenticado: Boolean(token), autenticar, logout }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
