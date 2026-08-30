// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { createContext, useState } from 'react'
import { AGENCIAS, AGENCIA_PADRAO } from '../config/branches'

const CHAVE_AGENCIA = 'iceibank.agenciaId'

export const BranchContext = createContext(null)

export function BranchProvider({ children }) {
  const [agenciaId, setAgenciaId] = useState(() => {
    const salva = localStorage.getItem(CHAVE_AGENCIA)
    return salva !== null ? Number(salva) : AGENCIA_PADRAO.id
  })

  function selecionarAgencia(id) {
    localStorage.setItem(CHAVE_AGENCIA, String(id))
    setAgenciaId(id)
  }

  const agencia = AGENCIAS.find((a) => a.id === agenciaId) ?? AGENCIA_PADRAO
  const value = { agencia, agencias: AGENCIAS, selecionarAgencia }
  return <BranchContext.Provider value={value}>{children}</BranchContext.Provider>
}
