// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend) — consome a funcionalidade adicional de historico
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { useState } from 'react'
import { historico } from '../api/contasApi'
import { useAuth } from '../hooks/useAuth'
import { useBranch } from '../hooks/useBranch'
import { useApiError } from '../hooks/useApiError'
import { ErrorBanner } from './ErrorBanner'

export function HistoricoList({ idContaPadrao }) {
  const [idConta, setIdConta] = useState(idContaPadrao ?? '')
  const [eventos, setEventos] = useState(null)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState(null)

  const { token } = useAuth()
  const { agencia } = useBranch()
  const { tratar } = useApiError()

  async function buscar(e) {
    e.preventDefault()
    setErro(null)
    setCarregando(true)
    try {
      setEventos(await historico(agencia.baseUrl, token, idConta))
    } catch (erroCapturado) {
      setErro(tratar(erroCapturado))
      setEventos(null)
    } finally {
      setCarregando(false)
    }
  }

  return (
    <div className="cartao">
      <h2>Histórico (extra)</h2>
      <ErrorBanner mensagem={erro} />
      <form onSubmit={buscar} className="linha">
        <div className="campo">
          <label htmlFor="idContaHist">Número da conta</label>
          <input id="idContaHist" value={idConta} onChange={(e) => setIdConta(e.target.value)} required />
        </div>
        <div style={{ alignSelf: 'flex-end', marginBottom: '1rem' }}>
          <button className="botao" type="submit" disabled={carregando}>
            {carregando ? '...' : 'Ver histórico'}
          </button>
        </div>
      </form>

      {eventos && (
        <ul className="lista-historico">
          {eventos.length === 0 && <li className="texto-mudo">Nenhum evento ainda.</li>}
          {eventos.map((evento, indice) => (
            <li key={indice}>
              <span className="tag-tipo">{evento.tipo}</span>
              <span className="texto-mudo">Lamport {evento.timestampLamport}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
