// Aluno: Gustavo Pessoa Firmino Duarte
// Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
// Projeto: ICEIBank - Sprint 1 - Parte G (Frontend)
// OFFSET pessoal (2 ultimos digitos da matricula): 47
//
// Pagina de referencia visual: consome o proprio GET /design-system (a
// mesma rota que o ThemeLoader usa para pintar o app) e mostra o conteudo
// de forma legivel - paleta com amostras de cor, tipografia, principios de
// UX, acessibilidade e as fontes da pesquisa. Nao e um "tema alternativo":
// e a prova visual de que a pesquisa feita para a Parte G virou dado
// consumivel por API, nao so texto solto no RESPOSTAS.md.
import { useEffect, useState } from 'react'
import { buscarDesignSystem } from '../api/designSystemApi'
import { useBranch } from '../hooks/useBranch'

export function DesignSystemPage({ aoVoltar }) {
  const { agencia } = useBranch()
  const [dados, setDados] = useState(null)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    let cancelado = false
    buscarDesignSystem(agencia.baseUrl)
      .then((resposta) => {
        if (!cancelado) setDados(resposta)
      })
      .catch((erroCapturado) => {
        if (!cancelado) setErro(erroCapturado.message ?? 'Falha ao carregar o design system.')
      })
    return () => {
      cancelado = true
    }
  }, [agencia.baseUrl])

  return (
    <div className="container container-largo">
      <div className="cabecalho-app">
        <div>
          <div className="marca" style={{ textAlign: 'left', marginBottom: 0 }}>
            Design System
          </div>
          <div className="usuario">Referência visual — GET /design-system ({agencia.label})</div>
        </div>
        <button className="link-sair" onClick={aoVoltar}>
          ← Voltar
        </button>
      </div>

      {erro && (
        <div className="banner banner-erro">
          <span>✕</span> {erro}
        </div>
      )}

      {!dados && !erro && <div className="texto-mudo">Carregando…</div>}

      {dados && (
        <>
          <div className="cartao">
            <h2>Resumo</h2>
            <p>{dados.resumo}</p>
            <p className="texto-mudo">
              Proporção recomendada de uso das cores: {dados.proporcaoRecomendada}
            </p>
          </div>

          <div className="cartao">
            <h2>Paleta de cores</h2>
            <div className="grade-paleta">
              {dados.paletaDeCores.map((cor) => (
                <div className="amostra-cor" key={cor.hex}>
                  <div className="amostra-cor-bloco" style={{ background: cor.hex }} />
                  <div className="amostra-cor-info">
                    <div className="amostra-cor-nome">{cor.nome}</div>
                    <div className="texto-mudo">
                      {cor.papel} · <code>{cor.hex}</code>
                    </div>
                    <div className="amostra-cor-uso">{cor.usoRecomendado}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="cartao">
            <h2>Tipografia</h2>
            <p style={{ fontFamily: dados.tipografia.pilhaDeFallback, fontSize: '1.5rem', fontWeight: 700 }}>
              {dados.tipografia.fontePrimaria} — Aa Bb Cc 123
            </p>
            <ul className="lista-simples">
              <li>
                <strong>Fonte primária:</strong> {dados.tipografia.fontePrimaria}
              </li>
              <li>
                <strong>Pilha de fallback:</strong> <code>{dados.tipografia.pilhaDeFallback}</code>
              </li>
              <li>
                <strong>Tamanho base do texto:</strong> {dados.tipografia.tamanhoBaseTexto}
              </li>
              <li>
                <strong>Peso dos títulos:</strong> {dados.tipografia.pesoTitulos}
              </li>
            </ul>
            <p className="texto-mudo">{dados.tipografia.observacao}</p>
          </div>

          <div className="cartao">
            <h2>Princípios de UX aplicados</h2>
            <ul className="lista-simples">
              {dados.principiosDeUx.map((principio) => (
                <li key={principio}>{principio}</li>
              ))}
            </ul>
          </div>

          <div className="cartao">
            <h2>Acessibilidade</h2>
            <ul className="lista-simples">
              <li>
                <strong>Contraste mínimo de texto:</strong> {dados.acessibilidade.contrasteTextoMinimo}
              </li>
              <li>
                <strong>Contraste mínimo de componentes:</strong>{' '}
                {dados.acessibilidade.contrasteComponentesMinimo}
              </li>
              <li>
                <strong>Regra de ouro:</strong> {dados.acessibilidade.regraDeOuro}
              </li>
            </ul>
            <p className="texto-mudo">{dados.acessibilidade.observacao}</p>
          </div>

          <div className="cartao">
            <h2>Fontes da pesquisa</h2>
            <ul className="lista-simples">
              {dados.fontesDaPesquisa.map((fonte) => (
                <li key={fonte.url}>
                  <a href={fonte.url} target="_blank" rel="noreferrer">
                    {fonte.titulo}
                  </a>
                  <div className="texto-mudo">{fonte.achadoPrincipal}</div>
                </li>
              ))}
            </ul>
          </div>
        </>
      )}
    </div>
  )
}
