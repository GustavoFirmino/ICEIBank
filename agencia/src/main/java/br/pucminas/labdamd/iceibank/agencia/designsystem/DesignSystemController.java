/*
 * Aluno: Gustavo Pessoa Firmino Duarte
 * Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
 * Projeto: ICEIBank - Sprint 1 - Preparacao do frontend (Parte G)
 * OFFSET pessoal (2 ultimos digitos da matricula): 47
 *
 * Endpoint de REFERENCIA (nao faz parte do escopo obrigatorio do roteiro):
 * expoe, como dado consumivel pela API, o resultado de uma pesquisa sobre
 * boas praticas de UI/UX e cores para aplicativos bancarios/fintech, feita
 * antes de implementar o frontend (Parte G). A ideia e que a paleta e os
 * principios de design do frontend nao fiquem "inventados" - vem de uma
 * pesquisa real, documentada e citavel, exposta aqui pra o frontend (ou
 * qualquer outro cliente) poder consumir programaticamente.
 *
 * Rota publica (nao exige JWT) - e informacao de referencia, nao um dado
 * de conta bancaria; faz sentido estar disponivel ate antes do login (a
 * propria tela de login pode consumi-la para se estilizar).
 */
package br.pucminas.labdamd.iceibank.agencia.designsystem;

import br.pucminas.labdamd.iceibank.agencia.designsystem.dto.AcessibilidadeInfo;
import br.pucminas.labdamd.iceibank.agencia.designsystem.dto.CorPaleta;
import br.pucminas.labdamd.iceibank.agencia.designsystem.dto.DesignSystemResponse;
import br.pucminas.labdamd.iceibank.agencia.designsystem.dto.FonteDePesquisa;
import br.pucminas.labdamd.iceibank.agencia.designsystem.dto.TipografiaInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DesignSystemController {

    @GetMapping("/design-system")
    public DesignSystemResponse designSystem() {
        return new DesignSystemResponse(
                "Paleta e principios de UI/UX para o frontend do ICEIBank (Parte G), "
                        + "baseados em pesquisa sobre design de apps bancarios/fintech "
                        + "(psicologia das cores, contraste de acessibilidade WCAG e "
                        + "praticas de UX de bancos digitais) - ver fontesDaPesquisa.",
                paletaDeCores(),
                "Regra 80/15/5: ~80% da tela em neutros (fundo/superficie/texto), "
                        + "~15% na cor primaria (azul, para navegacao e identidade), "
                        + "~5% em cores de destaque (verde/laranja/vermelho, so para "
                        + "acoes e status pontuais) - evita uma interface que "
                        + "\"grita\" e mantem o foco nos numeros da conta.",
                tipografia(),
                principiosDeUx(),
                acessibilidade(),
                fontesDaPesquisa()
        );
    }

    private List<CorPaleta> paletaDeCores() {
        return List.of(
                new CorPaleta("primaria", "Azul-marinho", "#0A2540",
                        "Cor de identidade/marca - cabecalhos, navegacao, elementos estruturais. "
                                + "Mesmo tom usado pela Stripe; azul e a cor mais associada a confianca, "
                                + "seguranca e seriedade em apps financeiros."),
                new CorPaleta("primaria-acao", "Azul vibrante", "#2563EB",
                        "Botoes de acao primaria, links, foco de campos - precisa ser mais "
                                + "vivo que o azul-marinho para se destacar como \"clicavel\"."),
                new CorPaleta("sucesso", "Verde", "#16A34A",
                        "Confirmacoes: deposito/transferencia concluida, saldo positivo. "
                                + "Verde e a cor mais associada a seguranca e aprovacao em fintechs."),
                new CorPaleta("atencao", "Laranja", "#F97316",
                        "Avisos que precisam de atencao mas NAO sao erros criticos (ex.: "
                                + "\"limite proximo\", a limitacao conhecida da Parte D). Bancos digitais "
                                + "recentes usam laranja no lugar de vermelho aqui para nao gerar panico."),
                new CorPaleta("erro", "Vermelho", "#DC2626",
                        "Reservado para erros reais: saldo insuficiente, token expirado/invalido, "
                                + "falha de comunicacao entre agencias. Usar com moderacao - vermelho "
                                + "demais aumenta a percepcao de risco mesmo em telas neutras."),
                new CorPaleta("fundo", "Cinza muito claro", "#F8FAFC",
                        "Fundo geral das telas - nao usar branco puro, cansa menos a vista em "
                                + "sessoes longas de uso."),
                new CorPaleta("superficie", "Branco", "#FFFFFF",
                        "Cartoes, modais e campos de formulario - contraste com o fundo cinza claro."),
                new CorPaleta("texto", "Cinza-azulado escuro", "#0F172A",
                        "Texto principal - preto puro (#000000) e evitado por gerar contraste "
                                + "excessivo/cansativo; este tom ja cumpre WCAG AA sobre fundo branco."),
                new CorPaleta("borda", "Cinza claro", "#E2E8F0",
                        "Bordas de campos e divisores - discreta o bastante para nao competir com o conteudo.")
        );
    }

    private TipografiaInfo tipografia() {
        return new TipografiaInfo(
                "Inter",
                "-apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
                "16px",
                "600-700 (semi-bold/bold)",
                "Fonte sans-serif do sistema: carrega rapido (sem depender de fonte "
                        + "externa pesada), e testada para legibilidade de numeros - importante "
                        + "num app que mostra saldo/valores o tempo todo. Tamanho base 16px "
                        + "evita que o navegador precise dar zoom automatico em campos de formulario "
                        + "no mobile."
        );
    }

    private List<String> principiosDeUx() {
        return List.of(
                "Hierarquia clara: saldo e acoes principais (transferir/depositar/sacar) sempre visiveis sem rolar a tela",
                "Baixa carga cognitiva: uma acao por tela/formulario, sem pedir mais dados do que o necessario",
                "Feedback imediato: toda acao (sucesso, erro, carregando) precisa de uma resposta visual clara e rapida",
                "Nunca depender so de cor: erros/avisos sempre acompanhados de icone e texto (acessibilidade e clareza)",
                "Consistencia: mesmo componente visual para a mesma acao em toda a aplicacao (ex.: um unico estilo de botao primario)",
                "Indicadores de seguranca visiveis: deixar claro quando uma acao e sensivel (ex.: transferencia) antes de confirmar"
        );
    }

    private AcessibilidadeInfo acessibilidade() {
        return new AcessibilidadeInfo(
                "4.5:1 (WCAG 2.2, nivel AA, texto normal)",
                "3:1 (WCAG 2.2 - criterio 1.4.11, bordas de botao/campo/checkbox e indicador de foco)",
                "Nunca usar cor como unico sinal de significado - sempre combinar com icone, texto ou padrao",
                "Todas as cores acima foram escolhidas para manter pelo menos 4.5:1 de contraste "
                        + "de texto sobre fundo branco (#FFFFFF) ou sobre o fundo claro (#F8FAFC)."
        );
    }

    private List<FonteDePesquisa> fontesDaPesquisa() {
        return List.of(
                new FonteDePesquisa(
                        "Banking App UX: Top 10 Best Practices in 2025 (Adam Fard)",
                        "https://adamfard.com/blog/banking-app-ux",
                        "Hierarquia de informacao, consistencia de design system e comunicacao clara de seguranca (ex.: 2FA) aumentam a confianca do usuario."),
                new FonteDePesquisa(
                        "Color Psychology in Fintech UI: Why Green Dominates (Billcut)",
                        "https://www.billcut.com/blogs/color-psychology-in-fintech-ui-why-green-dominates/",
                        "Verde comunica seguranca/aprovacao, azul comunica estabilidade, laranja substitui o vermelho para avisos sem gerar panico."),
                new FonteDePesquisa(
                        "Best Color Palettes for Fintech Apps: Trust, Security, and Style (InspoAI)",
                        "https://www.inspoai.io/blog/best-color-palette-for-fintech-app",
                        "Paleta de referencia da Stripe (#0A2540 primaria, #635BFF destaque) e a regra de proporcao 80% neutro / 15% primaria / 5% destaque."),
                new FonteDePesquisa(
                        "Fintech App Accessibility Best Practices (Netguru / resumo WCAG)",
                        "https://www.netguru.com/blog/fintech-app-accessibility",
                        "Contraste minimo WCAG AA de 4.5:1 para texto e 3:1 para componentes de interface (WCAG 2.2, criterio 1.4.11).")
        );
    }
}
