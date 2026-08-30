// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Um unico formulario para transferencia local E entre agencias - o
// frontend nao precisa saber a diferenca (o backend resolve sozinho pela
// particao das contas), mas a mensagem de resultado deixa claro qual dos
// dois caminhos foi usado.
import { useState } from 'react'
import { transferir } from '../api/transferenciasApi'
import { useAuth } from '../hooks/useAuth'
import { useBranch } from '../hooks/useBranch'
import { useApiError } from '../hooks/useApiError'
import { ErrorBanner } from './ErrorBanner'

export function TransferenciaForm({ idContaPadrao, onTransferenciaConcluida }) {
  const [idOrigem, setIdOrigem] = useState(idContaPadrao ?? '')
  const [idDestino, setIdDestino] = useState('')
  const [valor, setValor] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState(null)
  const [sucesso, setSucesso] = useState(null)

  const { token } = useAuth()
  const { agencia } = useBranch()
  const { tratar } = useApiError()

  async function aoSubmeter(e) {
    e.preventDefault()
    setErro(null)
    setSucesso(null)
    setCarregando(true)
    try {
      const resposta = await transferir(agencia.baseUrl, token, idOrigem, idDestino, valor)
      setSucesso(resposta.mensagem + (resposta.repetida ? ' (requisição repetida — não aplicada de novo)' : ''))
      setValor('')
      onTransferenciaConcluida?.()
    } catch (erroCapturado) {
      // Cobre inclusive o caso da "falha conhecida" (Parte D): a agência de
      // destino fora do ar responde 502, e o erro aparece aqui, visível.
      setErro(tratar(erroCapturado))
    } finally {
      setCarregando(false)
    }
  }

  return (
    <form onSubmit={aoSubmeter} className="cartao">
      <h2>Transferência</h2>
      <ErrorBanner mensagem={erro} />
      <ErrorBanner mensagem={sucesso} tipo="sucesso" />
      <div className="linha">
        <div className="campo">
          <label htmlFor="idOrigem">Conta de origem</label>
          <input id="idOrigem" value={idOrigem} onChange={(e) => setIdOrigem(e.target.value)} required />
        </div>
        <div className="campo">
          <label htmlFor="idDestino">Conta de destino</label>
          <input id="idDestino" value={idDestino} onChange={(e) => setIdDestino(e.target.value)} required />
        </div>
      </div>
      <div className="campo">
        <label htmlFor="valorTransf">Valor</label>
        <input
          id="valorTransf"
          type="number"
          min="1"
          step="1"
          value={valor}
          onChange={(e) => setValor(e.target.value)}
          required
        />
      </div>
      <button className="botao" type="submit" disabled={carregando}>
        {carregando ? 'Transferindo...' : 'Transferir'}
      </button>
    </form>
  )
}
