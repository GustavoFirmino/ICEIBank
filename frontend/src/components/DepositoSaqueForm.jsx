// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { useState } from 'react'
import { depositar, sacar } from '../api/contasApi'
import { useAuth } from '../hooks/useAuth'
import { useBranch } from '../hooks/useBranch'
import { useApiError } from '../hooks/useApiError'
import { ErrorBanner } from './ErrorBanner'

export function DepositoSaqueForm({ idContaPadrao, onOperacaoConcluida }) {
  const [idConta, setIdConta] = useState(idContaPadrao ?? '')
  const [valor, setValor] = useState('')
  const [carregando, setCarregando] = useState(null) // 'deposito' | 'saque' | null
  const [erro, setErro] = useState(null)
  const [sucesso, setSucesso] = useState(null)

  const { token } = useAuth()
  const { agencia } = useBranch()
  const { tratar } = useApiError()

  async function executar(operacao) {
    setErro(null)
    setSucesso(null)
    setCarregando(operacao)
    try {
      const chamada = operacao === 'deposito' ? depositar : sacar
      const conta = await chamada(agencia.baseUrl, token, idConta, Number(valor))
      setSucesso(
        `${operacao === 'deposito' ? 'Depósito' : 'Saque'} de ${Number(valor).toLocaleString('pt-BR', {
          style: 'currency',
          currency: 'BRL',
        })} concluído. Novo saldo: ${conta.saldo.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}`
      )
      setValor('')
      onOperacaoConcluida?.(conta)
    } catch (erroCapturado) {
      setErro(tratar(erroCapturado))
    } finally {
      setCarregando(null)
    }
  }

  return (
    <div className="cartao">
      <h2>Depósito / Saque</h2>
      <ErrorBanner mensagem={erro} />
      <ErrorBanner mensagem={sucesso} tipo="sucesso" />
      <div className="campo">
        <label htmlFor="idContaMov">Número da conta</label>
        <input id="idContaMov" value={idConta} onChange={(e) => setIdConta(e.target.value)} required />
      </div>
      <div className="campo">
        <label htmlFor="valorMov">Valor</label>
        <input
          id="valorMov"
          type="number"
          min="1"
          step="1"
          value={valor}
          onChange={(e) => setValor(e.target.value)}
          required
        />
      </div>
      <div className="linha">
        <button
          className="botao"
          type="button"
          disabled={carregando !== null}
          onClick={() => executar('deposito')}
        >
          {carregando === 'deposito' ? 'Depositando...' : 'Depositar'}
        </button>
        <button
          className="botao botao-secundario"
          type="button"
          disabled={carregando !== null}
          onClick={() => executar('saque')}
        >
          {carregando === 'saque' ? 'Sacando...' : 'Sacar'}
        </button>
      </div>
    </div>
  )
}
