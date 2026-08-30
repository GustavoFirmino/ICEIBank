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
├── agencia/       Serviço Spring Boot (Java 17, Maven)
├── frontend/      Interface web (React + Vite)
├── evidencias/sprint1/   Prints de execução exigidos pelo roteiro
├── RESPOSTAS.md   Respostas às perguntas de reflexão do roteiro
└── README.md
```

## Portas (porta-base 4000 + OFFSET 47)

| Agência | Porta |
|---|---|
| Agência 0 | 4047 |
| Agência 1 | 4048 |
| Agência 2 | 4049 |
| Frontend (Vite, dev) | 5173 (padrão) |

## Como rodar o backend (3 agências)

Pré-requisitos: JDK 17+, Maven 3.8+.

> **Nota:** se o seu caminho de pasta tiver acento (ex.: "Área de Trabalho"), `mvn spring-boot:run` falha com `ClassNotFoundException` — é um bug conhecido do Maven/Java em lidar com caracteres acentuados no classpath no Windows. A solução é empacotar o `.jar` e rodá-lo diretamente com `java -jar`, que não tem esse problema.

```powershell
cd agencia
mvn -q package -DskipTests

# Terminal 1
java -jar target/agencia-1.0.0.jar --spring.profiles.active=agencia0

# Terminal 2
java -jar target/agencia-1.0.0.jar --spring.profiles.active=agencia1

# Terminal 3
java -jar target/agencia-1.0.0.jar --spring.profiles.active=agencia2
```

Se seu caminho **não** tiver acentos, `mvn spring-boot:run -Dspring-boot.run.profiles=agencia0` também funciona normalmente.

## Como rodar o script de linha do tempo unificada

Com as 3 agências já tendo gerado eventos (pasta `agencia/data/*.jsonl`):

```powershell
cd agencia
mvn -q compile exec:java "-Dexec.mainClass=br.pucminas.labdamd.iceibank.agencia.ferramentas.MesclarLogs"
```

## Como rodar o frontend

Pré-requisitos: Node.js 20 LTS+.

```powershell
cd frontend
npm install
npm run dev
```

Abra `http://localhost:5173`, escolha a agência de entrada e faça login com um dos usuários de demonstração (ver `agencia/src/main/resources/application.yml`).

## Documentação

- Respostas às perguntas de cada parte do roteiro, decisões de design (login, autenticação entre agências) e descrição das funcionalidades adicionais: [`RESPOSTAS.md`](RESPOSTAS.md).
- Evidências de execução: [`evidencias/sprint1/`](evidencias/sprint1).
