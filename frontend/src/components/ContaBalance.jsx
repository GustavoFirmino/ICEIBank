// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { useState } from 'react'
import { consultarConta } from '../api/contasApi'
import { useAuth } from '../hooks/useAuth'
import { useBranch } from '../hooks/useBranch'
import { useApiError } from '../hooks/useApiError'
import { ErrorBanner } from './ErrorBanner'

export function ContaBalance({ conta, onContaCarregada }) {
  const [idConta, setIdConta] = useState(conta?.id ?? '')
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState(null)

  const { token } = useAuth()
  const { agencia } = useBranch()
  const { tratar } = useApiError()

  async function consultar(e) {
    e.preventDefault()
    setErro(null)
    setCarregando(true)
    try {
      const resposta = await consultarConta(agencia.baseUrl, token, idConta)
      onContaCarregada(resposta)
    } catch (erroCapturado) {
      setErro(tratar(erroCapturado))
      onContaCarregada(null)
    } finally {
      setCarregando(false)
    }
  }

  return (
    <div className="cartao">
      <h2>Consultar saldo</h2>
      <ErrorBanner mensagem={erro} />
      <form onSubmit={consultar} className="linha">
        <div className="campo">
          <label htmlFor="idConta">Número da conta</label>
          <input id="idConta" value={idConta} onChange={(e) => setIdConta(e.target.value)} required />
        </div>
        <div style={{ alignSelf: 'flex-end', marginBottom: '1rem' }}>
          <button className="botao" type="submit" disabled={carregando}>
            {carregando ? '...' : 'Consultar'}
          </button>
        </div>
      </form>

      {conta && (
        <>
          <div className="texto-mudo">{conta.titular} — conta {conta.id}</div>
          <div className="saldo">
            {conta.saldo.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
          </div>
        </>
      )}
    </div>
  )
}
