// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { useState } from 'react'
import { useAuth } from '../hooks/useAuth'
import { useBranch } from '../hooks/useBranch'
import { BranchSelector } from '../components/BranchSelector'
import { ContaBalance } from '../components/ContaBalance'
import { DepositoSaqueForm } from '../components/DepositoSaqueForm'
import { TransferenciaForm } from '../components/TransferenciaForm'
import { HistoricoList } from '../components/HistoricoList'

export function DashboardPage() {
  const [conta, setConta] = useState(null)
  const { username, logout } = useAuth()
  const { agencia } = useBranch()

  const idContaAtual = conta?.id

  return (
    <div className="container">
      <div className="cabecalho-app">
        <div>
          <div className="marca" style={{ textAlign: 'left', marginBottom: 0 }}>
            ICEIBank
          </div>
          <div className="usuario">
            {username} · conectado em {agencia.label}
          </div>
        </div>
        <button className="link-sair" onClick={logout}>
          Sair
        </button>
      </div>

      <div className="cartao">
        <BranchSelector />
      </div>

      <ContaBalance conta={conta} onContaCarregada={setConta} />
      <DepositoSaqueForm idContaPadrao={idContaAtual} onOperacaoConcluida={setConta} />
      <TransferenciaForm idContaPadrao={idContaAtual} />
      <HistoricoList idContaPadrao={idContaAtual} />
    </div>
  )
}
