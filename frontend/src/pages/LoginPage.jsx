// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { LoginForm } from '../components/LoginForm'

export function LoginPage({ aoAbrirDesignSystem }) {
  return (
    <div className="container">
      <div className="marca">ICEIBank</div>
      <div className="subtitulo">Banco simplificado em agências — LDAMD Sprint 1</div>
      <LoginForm />
      <div style={{ textAlign: 'center' }}>
        <button className="link-sair" onClick={aoAbrirDesignSystem}>
          Ver design system
        </button>
      </div>
    </div>
  )
}
