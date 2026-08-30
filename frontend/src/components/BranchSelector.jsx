// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
import { useBranch } from '../hooks/useBranch'

export function BranchSelector() {
  const { agencia, agencias, selecionarAgencia } = useBranch()

  return (
    <div className="campo">
      <label htmlFor="agencia">Agência (porta de entrada)</label>
      <select
        id="agencia"
        value={agencia.id}
        onChange={(e) => selecionarAgencia(Number(e.target.value))}
      >
        {agencias.map((a) => (
          <option key={a.id} value={a.id}>
            {a.label} — {a.baseUrl}
          </option>
        ))}
      </select>
    </div>
  )
}
