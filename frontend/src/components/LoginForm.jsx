// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { useState } from 'react'
import { useAuth } from '../hooks/useAuth'
import { useBranch } from '../hooks/useBranch'
import { useApiError } from '../hooks/useApiError'
import { BranchSelector } from './BranchSelector'
import { ErrorBanner } from './ErrorBanner'

export function LoginForm() {
  const [username, setUsername] = useState('gustavo')
  const [password, setPassword] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState(null)

  const { autenticar } = useAuth()
  const { agencia } = useBranch()
  const { tratar } = useApiError()

  async function aoSubmeter(e) {
    e.preventDefault()
    setErro(null)
    setCarregando(true)
    try {
      await autenticar(agencia.baseUrl, username, password)
    } catch (erroCapturado) {
      setErro(tratar(erroCapturado))
    } finally {
      setCarregando(false)
    }
  }

  return (
    <form onSubmit={aoSubmeter} className="cartao">
      <ErrorBanner mensagem={erro} />
      <BranchSelector />
      <div className="campo">
        <label htmlFor="username">Usuário</label>
        <input id="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
      </div>
      <div className="campo">
        <label htmlFor="password">Senha</label>
        <input
          id="password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
      </div>
      <button className="botao" type="submit" disabled={carregando}>
        {carregando ? 'Entrando...' : 'Entrar'}
      </button>
    </form>
  )
}
