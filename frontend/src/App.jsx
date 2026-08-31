// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { useState } from 'react'
import { AuthProvider } from './context/AuthContext'
import { BranchProvider } from './context/BranchContext'
import { ThemeLoader } from './ThemeLoader'
import { useAuth } from './hooks/useAuth'
import { LoginPage } from './pages/LoginPage'
import { DashboardPage } from './pages/DashboardPage'
import { DesignSystemPage } from './pages/DesignSystemPage'

// Sem biblioteca de rotas (ver Parte G do RESPOSTAS.md) - a troca para a
// pagina de referencia do design system e so mais um estado local, do mesmo
// jeito que autenticado/nao-autenticado ja decide entre Login e Dashboard.
function Rotas({ mostrarDesignSystem, aoAlternarDesignSystem }) {
  const { autenticado } = useAuth()
  if (mostrarDesignSystem) return <DesignSystemPage aoVoltar={aoAlternarDesignSystem} />
  return autenticado ? (
    <DashboardPage aoAbrirDesignSystem={aoAlternarDesignSystem} />
  ) : (
    <LoginPage aoAbrirDesignSystem={aoAlternarDesignSystem} />
  )
}

export default function App() {
  const [mostrarDesignSystem, setMostrarDesignSystem] = useState(false)

  return (
    <BranchProvider>
      <AuthProvider>
        <ThemeLoader />
        <Rotas
          mostrarDesignSystem={mostrarDesignSystem}
          aoAlternarDesignSystem={() => setMostrarDesignSystem((atual) => !atual)}
        />
      </AuthProvider>
    </BranchProvider>
  )
}
