// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { AuthProvider } from './context/AuthContext'
import { BranchProvider } from './context/BranchContext'
import { ThemeLoader } from './ThemeLoader'
import { useAuth } from './hooks/useAuth'
import { LoginPage } from './pages/LoginPage'
import { DashboardPage } from './pages/DashboardPage'

function Rotas() {
  const { autenticado } = useAuth()
  return autenticado ? <DashboardPage /> : <LoginPage />
}

export default function App() {
  return (
    <BranchProvider>
      <AuthProvider>
        <ThemeLoader />
        <Rotas />
      </AuthProvider>
    </BranchProvider>
  )
}
