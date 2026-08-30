// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// So le o campo "exp" do JWT no proprio navegador (nao valida assinatura -
// isso e trabalho do backend). Serve so para o frontend avisar a pessoa
// ANTES de mandar uma requisicao que a API certamente vai rejeitar com 401.

export function jwtEstaExpirado(token) {
  const exp = extrairExpiracao(token)
  if (exp === null) return false
  return Date.now() >= exp * 1000
}

function extrairExpiracao(token) {
  try {
    const [, payloadBase64] = token.split('.')
    const payload = JSON.parse(atob(payloadBase64.replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp === 'number' ? payload.exp : null
  } catch {
    return null
  }
}
