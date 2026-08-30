// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47

/** Erro tipado com o status HTTP e a mensagem que a API devolveu (campo "erro"). */
export class AppError extends Error {
  constructor(status, mensagem) {
    super(mensagem)
    this.status = status
  }
}
