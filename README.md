# ICEIBank

**Aluno:** Gustavo Pessoa Firmino Duarte
**Disciplina:** Laboratório de Desenvolvimento de Aplicações Móveis e Distribuídas
**OFFSET pessoal (2 últimos dígitos da matrícula):** 47

Banco simplificado dividido em agências, desenvolvido ao longo de 4 sprints para aplicar, na prática, os principais conceitos de Sistemas Distribuídos vistos na disciplina teórica.

| Sprint | Unidade | Tecnologia | Conceito de SD |
|---|---|---|---|
| **1 (este)** | U2 - Desenvolvimento Web | API REST/MVC (Spring Boot) + React | Relógio lógico de Lamport |
| 2 | U3 - Comunicação indireta | Mensageria / Pub-Sub | Relógio vetorial |
| 3 | U4 - Desenvolvimento Móvel | App Flutter | Consenso (eleição de líder) |
| 4 | U5 - Computação em Nuvem | Containers | Transações distribuídas (2PC/Saga) |

## Sprint 1 — escopo

- Serviço de agência em Spring Boot (Java), rodado 3 vezes com identidades diferentes (`agencia.id` = 0, 1, 2) = 3 agências independentes.
- Partição de contas: `id_conta % 3` decide a agência dona da conta.
- CRUD de contas + depósito/saque, tudo carimbado com relógio lógico de Lamport.
- Transferência local (mesma agência) e entre agências (chamada REST direta entre agências).
- Limitação conhecida e proposital: se a chamada à agência de destino falhar no meio de uma transferência entre agências, o débito já aplicado **não** é revertido automaticamente — isso é resolvido de verdade só no Sprint 4 (2PC/Saga). O sistema apenas registra a inconsistência no log.
- Script `MesclarLogs` que une os logs das 3 agências em uma linha do tempo ordenada por relógio de Lamport.
- Autenticação via JWT protegendo as rotas da API.
- Frontend web (React + Vite) consumindo a API autenticada.
- Funcionalidades adicionais: **idempotência de transferências** e **histórico de transações por conta**.

## Estrutura

```
ICEIBank/
├── agencia/               Serviço Spring Boot (Java 17) — inclui o Maven Wrapper (mvnw.cmd)
├── frontend/              Interface web (React + Vite)
├── evidencias/sprint1/    Prints de execução exigidos pelo roteiro
├── iniciar-agencias.ps1   Sobe as 3 agências em 3 janelas do PowerShell
├── linha-do-tempo.ps1     Roda o MesclarLogs (Parte E) direto do .jar
├── demo-auth.ps1          Mostra os 3 cenários de JWT da Parte F (sem token / válido / expirado)
├── RESPOSTAS.md           Respostas às perguntas de reflexão do roteiro
└── README.md
```

## Portas (porta-base 4000 + OFFSET 47)

| Agência | Porta |
|---|---|
| Agência 0 | 4047 |
| Agência 1 | 4048 |
| Agência 2 | 4049 |
| Frontend (Vite, dev) | 5173 (padrão) |

## Passo a passo completo (do zero até o frontend aberto)

Todos os comandos abaixo são para o **PowerShell** (o terminal padrão do Windows — abra pelo menu Iniciar digitando "PowerShell"; não precisa ser como administrador). Se preferir o **Git Bash**, as diferenças estão indicadas em cada passo.

**Passo 1 — clonar** (em qualquer pasta, de preferência curta e sem acento, ex.: `C:\dev`):

```powershell
git clone -c core.longpaths=true https://github.com/GustavoFirmino/ICEIBank.git
cd ICEIBank
```
*(Git Bash: mesmos comandos.)*

**Passo 2 — conferir os pré-requisitos** (precisa aparecer Java 17 ou superior e Node 20 ou superior):

```powershell
java -version
node --version
```
*(Git Bash: mesmos comandos.)* Se `java` não for encontrado, instale um JDK 17+ e **abra um terminal novo** depois de instalar.

**Passo 3 — subir as 3 agências** (abre 3 janelas do PowerShell, uma por agência; na primeira vez demora alguns minutos baixando o Maven e as dependências):

```powershell
.\iniciar-agencias.ps1
```
Se o PowerShell reclamar de "execução de scripts desabilitada", rode antes, no mesmo terminal: `Set-ExecutionPolicy -Scope Process Bypass` (vale só para essa janela).

*(Git Bash: o script é PowerShell — use o jeito manual, em 3 terminais separados:)*
```bash
cd agencia && ./mvnw -q -DskipTests package
java -jar target/agencia-1.0.0.jar --spring.profiles.active=agencia0   # terminal 1
java -jar target/agencia-1.0.0.jar --spring.profiles.active=agencia1   # terminal 2
java -jar target/agencia-1.0.0.jar --spring.profiles.active=agencia2   # terminal 3
```

Espere cada janela mostrar `Started AgenciaApplication`. Se o Firewall do Windows perguntar, clique em "Permitir acesso".

**Passo 4 — subir o frontend** (em um 4º terminal, na raiz do repositório):

```powershell
cd frontend
npm install
npm run dev
```
*(Git Bash: mesmos comandos.)* Abra `http://localhost:5173`, deixe "Agência 0" selecionada e entre com `gustavo` / `senha123`.

**Passo 5 — criar contas para testar** (o estado é em memória: toda vez que as agências sobem, começa vazio). Pelo frontend não dá para criar conta (não é requisito do roteiro), então crie pela API, em um 5º terminal:

```powershell
$token = (Invoke-RestMethod -Uri "http://localhost:4047/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"gustavo","password":"senha123"}').token
$h = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Uri "http://localhost:4047/contas" -Method Post -ContentType "application/json" -Headers $h -Body '{"id":0,"titular":"Ana","saldoInicial":1000}'
Invoke-RestMethod -Uri "http://localhost:4048/contas" -Method Post -ContentType "application/json" -Headers $h -Body '{"id":1,"titular":"Bruno","saldoInicial":500}'
Invoke-RestMethod -Uri "http://localhost:4049/contas" -Method Post -ContentType "application/json" -Headers $h -Body '{"id":2,"titular":"Carla","saldoInicial":300}'
Invoke-RestMethod -Uri "http://localhost:4047/contas" -Method Post -ContentType "application/json" -Headers $h -Body '{"id":3,"titular":"Davi","saldoInicial":200}'
```
*(Git Bash, com curl:)*
```bash
TOKEN=$(curl -s -X POST http://localhost:4047/auth/login -H "Content-Type: application/json" -d '{"username":"gustavo","password":"senha123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s -X POST http://localhost:4047/contas -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"id":0,"titular":"Ana","saldoInicial":1000}'
curl -s -X POST http://localhost:4048/contas -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"id":1,"titular":"Bruno","saldoInicial":500}'
curl -s -X POST http://localhost:4049/contas -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"id":2,"titular":"Carla","saldoInicial":300}'
curl -s -X POST http://localhost:4047/contas -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"id":3,"titular":"Davi","saldoInicial":200}'
```

Conta 0 e 3 → Agência 0 (porta 4047); conta 1 → Agência 1 (4048); conta 2 → Agência 2 (4049). Agora, no frontend: consulte a conta 0, faça depósito/saque, transfira 0 → 3 (local) e 0 → 1 (entre agências), e tente sacar mais do que o saldo para ver o erro.

**Passo 6 — linha do tempo de Lamport** (depois de gerar alguns eventos), na raiz do repositório:

```powershell
.\linha-do-tempo.ps1
```
*(Git Bash: `cd agencia && ./mvnw -q compile exec:java "-Dexec.mainClass=br.pucminas.labdamd.iceibank.agencia.ferramentas.MesclarLogs"` — as aspas em volta do `-Dexec...` são obrigatórias.)*

**Passo 7 — os 3 cenários de autenticação (Parte F)** de uma vez, na raiz do repositório:

```powershell
.\demo-auth.ps1
```

**Para parar tudo:** feche as janelas das agências (ou `Ctrl+C` em cada uma) e `Ctrl+C` no terminal do frontend.

## Como clonar (leia antes — evita o erro `Filename too long`)

Os pacotes Java do projeto geram caminhos longos, e o Git no Windows limita caminhos a 260 caracteres por padrão. Clonando dentro de pastas como `OneDrive\Área de Trabalho\...`, o `git clone` pode falhar com `error: unable to create file ...: Filename too long`. Clone assim (não precisa de permissão de administrador):

```powershell
git clone -c core.longpaths=true https://github.com/GustavoFirmino/ICEIBank.git
```

Se já clonou e deu o erro, rode dentro da pasta: `git config core.longpaths true` e depois `git restore --source=HEAD :/`.

## Como rodar o backend (3 agências)

Pré-requisito: **só o JDK 17 ou superior** (`java -version`). Maven **não** precisa estar instalado — o projeto inclui o Maven Wrapper (`agencia/mvnw.cmd`), que baixa a versão certa sozinho na primeira execução.

**Jeito rápido (abre as 3 agências em 3 janelas do PowerShell):**

```powershell
.\iniciar-agencias.ps1
```

**Jeito manual:**

```powershell
cd agencia
.\mvnw.cmd -q -DskipTests package

# Terminal 1
java -jar target/agencia-1.0.0.jar --spring.profiles.active=agencia0

# Terminal 2
java -jar target/agencia-1.0.0.jar --spring.profiles.active=agencia1

# Terminal 3
java -jar target/agencia-1.0.0.jar --spring.profiles.active=agencia2
```

> **Por que `java -jar` e não `spring-boot:run`?** Se o caminho da pasta tiver acento (ex.: "Área de Trabalho"), `mvnw spring-boot:run` falha com `ClassNotFoundException` — bug conhecido do Maven/Java com caracteres acentuados no classpath no Windows. O `.jar` empacotado não tem esse problema. Se o seu caminho **não** tiver acentos, `.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=agencia0` também funciona.

**Credenciais de demonstração** (definidas em `agencia/src/main/resources/application.yml`, iguais nas 3 agências):

| Usuário | Senha |
|---|---|
| `gustavo` | `senha123` |
| `aluno` | `senha123` |

## Como rodar o script de linha do tempo unificada

Com as 3 agências já tendo gerado eventos (pasta `agencia/data/*.jsonl`), na raiz do repositório:

```powershell
.\linha-do-tempo.ps1
```

O script roda a classe `MesclarLogs` direto do `.jar` empacotado (via `PropertiesLauncher` do Spring Boot), sem passar pelo Maven. O caminho "manual", via Maven, também funciona — atenção às aspas em volta do `-D...`, sem elas o PowerShell quebra o argumento no ponto:

```powershell
cd agencia
.\mvnw.cmd -q compile exec:java "-Dexec.mainClass=br.pucminas.labdamd.iceibank.agencia.ferramentas.MesclarLogs"
```

## Endpoints da API

Todas as rotas abaixo (exceto `/auth/login`, `/contas/{id}/creditar-remoto` e `/design-system`) exigem o header `Authorization: Bearer <token>` (ver Parte F).

| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/login` | Login (`{"username","password"}`), devolve `{"token","expiraEmSegundos"}` |
| POST | `/contas` | Cria conta (`{"id","titular","saldoInicial"}`) |
| GET | `/contas/{id}` | Consulta saldo |
| POST | `/contas/{id}/depositar` | Deposita (`{"valor"}`) |
| POST | `/contas/{id}/sacar` | Saca (`{"valor"}`) |
| GET | `/contas/{id}/historico` | **Extra:** histórico de eventos da conta |
| POST | `/transferencias` | Transfere (`{"idOrigem","idDestino","valor","idOperacao"}` — `idOperacao` é opcional; ver **Extra: idempotência** abaixo) |
| POST | `/contas/{id}/creditar-remoto` | Interna, agência-a-agência (`X-Internal-Key`, não JWT) |
| GET | `/design-system` | Rota pública de referência: paleta de cores, tipografia e princípios de UX pesquisados para o frontend (Parte G) — ver seção abaixo |

### Paleta e princípios de design (`GET /design-system`)

Antes de implementar o frontend, pesquisei boas práticas de UI/UX para apps bancários/fintech (psicologia das cores, contraste de acessibilidade WCAG, práticas de UX de bancos digitais) e expus o resultado como um endpoint da própria API — assim a paleta não fica "inventada", vem de uma pesquisa real e citável, consumível programaticamente pelo frontend (inclusive na tela de login, antes de qualquer autenticação).

**Resumo da pesquisa:**
- **Cores:** azul-marinho (`#0A2540`, inspirado na Stripe) para identidade/confiança, azul vibrante (`#2563EB`) para ações, verde (`#16A34A`) para sucesso, laranja (`#F97316`) para avisos (no lugar do vermelho, evita gerar pânico), vermelho (`#DC2626`) reservado só para erros reais.
- **Proporção:** regra 80/15/5 — 80% neutros, 15% cor primária, 5% cores de destaque.
- **Tipografia:** fonte sans-serif do sistema (Inter + fallback nativo), 16px base.
- **Acessibilidade:** contraste mínimo WCAG AA — 4.5:1 para texto, 3:1 para bordas de componentes.

Fontes completas (com links) disponíveis na resposta do próprio endpoint, campo `fontesDaPesquisa`.

**Idempotência:** se `idOperacao` for informado em `POST /transferencias` e a mesma requisição for reenviada com o mesmo valor, a transferência não é aplicada de novo — a resposta volta com `"repetida": true` e o saldo não muda uma segunda vez.

## Como rodar o frontend

Pré-requisitos: Node.js 20 LTS+.

```powershell
cd frontend
npm install
npm run dev
```

Abra `http://localhost:5173`, escolha a agência de entrada e faça login com `gustavo` / `senha123` (ou `aluno` / `senha123`). As 3 agências precisam estar no ar antes — o frontend só conversa com a API.

## Rodando em outro computador (laboratório): problemas comuns

| Sintoma | Causa | O que fazer |
|---|---|---|
| `git clone` falha com `Filename too long` | Limite de 260 caracteres de caminho do Git no Windows | Clonar com `git clone -c core.longpaths=true <url>` (ver seção "Como clonar") |
| `'mvn' não é reconhecido como um comando` | Maven não instalado | Não precisa instalar: use `.\mvnw.cmd` no lugar de `mvn` (Maven Wrapper incluso) |
| `.\mvnw.cmd` demora muito na primeira vez | Está baixando o Maven e as dependências do projeto (~100 MB) | Normal; só na primeira execução. Precisa de internet |
| Erro de compilação `release version 17 not supported` / `invalid target release: 17` | JDK antigo (8 ou 11) | Instalar JDK 17+ e confirmar com `java -version` em um terminal **novo** |
| `ClassNotFoundException` ao usar `spring-boot:run` | Caminho da pasta com acento | Usar `java -jar target/agencia-1.0.0.jar ...` (ver "Como rodar o backend") |
| `Web server failed to start. Port 4047 was already in use` | Outra instância da agência (ou outro programa) já usa a porta | Fechar a janela antiga, ou `Get-NetTCPConnection -LocalPort 4047 \| Select OwningProcess` e encerrar o processo |
| Firewall do Windows pede permissão ao subir a agência | Primeira execução de um servidor Java na máquina | Clicar em "Permitir acesso" |
| Frontend abre, mas login dá "Falha de rede" / erro de CORS | Agências não estão no ar, ou o frontend está em outra porta que não `5173` | Subir as 3 agências primeiro; manter o Vite na porta padrão (o CORS do backend libera só `http://localhost:5173`) |
| `npm run dev` falha com erro de sintaxe / `Unexpected token` | Node.js antigo | Instalar Node.js 20 LTS ou superior (`node --version`) |
| Contas "sumiram" depois de reiniciar uma agência | Estado é em memória, por decisão do roteiro (Sprint 1 não tem banco) | Esperado — recriar as contas via API/frontend |

## Vídeo de apresentação

Funcionalidades e principais decisões do projeto (≈10 min), gravado em 07/09/2026: **[assistir / baixar](https://github.com/GustavoFirmino/ICEIBank/releases/download/v1.0-sprint1/iceibank-sprint1-apresentacao.mp4)** — publicado como asset da release [`v1.0-sprint1`](https://github.com/GustavoFirmino/ICEIBank/releases/tag/v1.0-sprint1) (o arquivo passa do limite de 100 MB do Git, por isso não está dentro do repositório).

## Documentação

- Respostas às perguntas de cada parte do roteiro, decisões de design (login, autenticação entre agências) e descrição das funcionalidades adicionais: [`RESPOSTAS.md`](RESPOSTAS.md).
- Evidências de execução: [`evidencias/sprint1/`](evidencias/sprint1). Os prints de API/terminal foram gerados a partir da **saída real** dos comandos (executados contra as 3 agências rodando, com `Get-Date` no início de cada um) e os do frontend a partir do app React rodando de verdade — nenhum resultado foi editado ou montado à mão.
