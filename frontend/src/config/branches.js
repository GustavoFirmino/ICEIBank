// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Portas = porta-base (4000) + OFFSET pessoal (47). Cada agencia so responde
// pelas contas que sao dela (id % 3) - o frontend deixa escolher qual e a
// "porta de entrada", mas nao precisa saber a logica de particionamento:
// o backend resolve local vs. entre-agencias sozinho.
export const AGENCIAS = [
  { id: 0, label: 'Agência 0', baseUrl: 'http://localhost:4047' },
  { id: 1, label: 'Agência 1', baseUrl: 'http://localhost:4048' },
  { id: 2, label: 'Agência 2', baseUrl: 'http://localhost:4049' },
]

export const AGENCIA_PADRAO = AGENCIAS[0]
