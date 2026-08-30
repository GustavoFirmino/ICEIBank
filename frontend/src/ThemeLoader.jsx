// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Busca GET /design-system (rota publica) uma vez, ao abrir o app, e
// sobrescreve as variaveis CSS com a paleta pesquisada - assim o frontend
// realmente CONSOME o endpoint de design, em vez de so ter os mesmos
// valores copiados manualmente no index.css (que ficam so como fallback
// caso a agencia esteja fora do ar quando a tela abre).
import { useEffect } from 'react'
import { buscarDesignSystem } from './api/designSystemApi'
import { useBranch } from './hooks/useBranch'

const MAPA_PAPEL_PARA_VARIAVEL = {
  primaria: '--cor-primaria',
  'primaria-acao': '--cor-primaria-acao',
  sucesso: '--cor-sucesso',
  atencao: '--cor-atencao',
  erro: '--cor-erro',
  fundo: '--cor-fundo',
  superficie: '--cor-superficie',
  texto: '--cor-texto',
  borda: '--cor-borda',
}

export function ThemeLoader() {
  const { agencia } = useBranch()

  useEffect(() => {
    let cancelado = false
    buscarDesignSystem(agencia.baseUrl)
      .then((designSystem) => {
        if (cancelado) return
        designSystem.paletaDeCores.forEach((cor) => {
          const variavel = MAPA_PAPEL_PARA_VARIAVEL[cor.papel]
          if (variavel) {
            document.documentElement.style.setProperty(variavel, cor.hex)
          }
        })
        if (designSystem.tipografia?.fontePrimaria) {
          document.documentElement.style.setProperty(
            '--fonte-base',
            `${designSystem.tipografia.fontePrimaria}, ${designSystem.tipografia.pilhaDeFallback}`
          )
        }
      })
      .catch(() => {
        // Sem problema: os valores default do index.css ja cobrem esse caso.
      })
    return () => {
      cancelado = true
    }
  }, [agencia.baseUrl])

  return null
}
