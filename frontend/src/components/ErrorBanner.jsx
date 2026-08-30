// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Erro tratado de forma VISIVEL na interface (nao so no console do
// navegador) - requisito explicito da Parte G.
export function ErrorBanner({ mensagem, tipo = 'erro' }) {
  if (!mensagem) return null
  const icone = tipo === 'sucesso' ? '✓' : tipo === 'atencao' ? '⚠' : '✕'
  return (
    <div className={`banner banner-${tipo}`} role="alert">
      <span>{icone}</span>
      <span>{mensagem}</span>
    </div>
  )
}
