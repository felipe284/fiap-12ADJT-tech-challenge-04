# Tech Challenge 04 - Plataforma de Feedback (Azure Serverless)

Este repositório contém a solução do Tech Challenge Fase 4: uma plataforma em que estudantes avaliam aulas e administradores recebem alertas de avaliações críticas e um relatório semanal.

O projeto foi implementado com funções serverless em Java rodando no Azure Functions, banco Cosmos DB, fila no Storage, envio de e-mails com Azure Communication Services, monitoramento com Application Insights, infraestrutura com Terraform e deploy automatizado com GitHub Actions.

Tecnologias principais:

- Java 21
- Quarkus 3.40 (`quarkus-azure-functions`)
- Azure Functions (plano Consumption, Linux)
- Azure Cosmos DB (NoSQL, free tier)
- Azure Storage Queue
- Azure Communication Services (E-mail)
- Azure Application Insights / Log Analytics
- Terraform
- GitHub Actions
- JUnit 5 / Mockito

---

## Arquitetura

```text
Aluno --POST /api/avaliacao (function key)--> [receberAvaliacao] --> Cosmos DB
                                                   | nota de 0 a 3
                                                   v
                                      Storage Queue "avaliacoes-criticas"
                                                   v
                                          [notificarCritico] --> e-mail --> Administrador

Timer (segunda, 08:00 UTC) --> [relatorioSemanal] --> Cosmos DB (últimos 7 dias) --> e-mail --> Administrador

Application Insights --> Alertas --> Action Group --> e-mail --> Administrador
```

Modelo de nuvem: PaaS/FaaS na Azure, com cobrança por uso, na região `brazilsouth`.

| Componente | Função |
|---|---|
| Azure Functions | Executa as três funções serverless |
| Cosmos DB | Armazena as avaliações (partição `/dia`) |
| Storage Queue | Separa o recebimento da avaliação do envio do e-mail |
| Communication Services | Envia os e-mails com domínio gerenciado pela Azure |
| Application Insights / Log Analytics | Logs, falhas e métricas |
| Azure Monitor Alerts + Action Group | Avisa o administrador sobre problemas críticos |
| Terraform | Cria toda a infraestrutura |
| GitHub Actions | Build, testes e deploy das funções |

---

## Funções serverless

Cada função tem uma única responsabilidade:

| Função | Gatilho | Responsabilidade |
|---|---|---|
| `receberAvaliacao` | HTTP `POST /api/avaliacao` | Valida, classifica a urgência, grava e publica as críticas na fila |
| `notificarCritico` | Fila `avaliacoes-criticas` | Envia o e-mail de urgência ao administrador |
| `relatorioSemanal` | Timer `0 0 8 * * MON` | Gera o relatório dos últimos 7 dias e envia por e-mail |

Regra de urgência:

- Nota de 0 a 3: `CRITICA`
- Nota de 4 a 6: `MEDIA`
- Nota de 7 a 10: `BAIXA`

Dados do e-mail de urgência: descrição, urgência e data de envio.

Dados do relatório semanal: descrição, urgência e data de envio de cada avaliação, média das notas, quantidade de avaliações por dia e quantidade por urgência.

---

## Endpoint REST

```text
POST https://fiap-feedback-fm-tc4.azurewebsites.net/api/avaliacao
```

Cabeçalhos:

- `Content-Type: application/json`
- `x-functions-key: <chave>`

Corpo:

```json
{
  "descricao": "Aula confusa e audio ruim",
  "nota": 2
}
```

Resposta (`201 Created`):

```json
{
  "id": "c1e40a5e-e381-4a05-945d-a0b01333c300",
  "urgencia": "CRITICA"
}
```

Respostas de erro:

- `400 Bad Request`: JSON inválido, descrição vazia ou ausente, ou nota que não seja inteiro de 0 a 10
- `401 Unauthorized`: chave ausente ou inválida
- `500 Internal Server Error`: falha ao gravar a avaliação

Para obter a chave:

```bash
az functionapp keys list -g rg-fiap-feedback-tf -n fiap-feedback-fm-tc4 --query functionKeys.default -o tsv
```

---

## Segurança e governança de acesso

- O endpoint exige a function key. A chave é compartilhada pelos estudantes e não identifica o usuário.
- A função acessa o Cosmos DB com Managed Identity e permissão de dados (`Cosmos DB Built-in Data Contributor`), sem chaves no código.
- O administrador não acessa a API: recebe e-mails e acessa o portal Azure conforme as permissões da assinatura.
- HTTPS obrigatório, TLS 1.2 mínimo, FTP desabilitado e acesso público ao Storage desabilitado.
- A configuração de e-mail fica nas configurações da Function App, que o Terraform preenche. O estado do Terraform contém essas informações e fica em um container privado.

Decisão registrada: a separação entre aluno e administrador usa chave e permissões do portal, sem Microsoft Entra ID, conforme orientação do professor de que serviços do provedor são aceitos desde que documentados.

---

## Pré-requisitos

Antes de iniciar, verifique se possui:

- JDK 21
- Azure CLI
- Terraform 1.5 ou superior
- Conta Azure (Azure for Students)

Comandos úteis:

```bash
java -version
az version
terraform version
```

---

## Infraestrutura com Terraform

Os arquivos ficam na pasta `infra/`.

### 1. Autenticação

```bash
az login
az account show
```

### 2. Armazenamento do estado (uma única vez)

O estado do Terraform fica em um container privado, em um grupo separado, para não ser apagado junto com a aplicação. O nome da conta de armazenamento é único na Azure; se precisar trocar, altere também o bloco `backend` em `infra/main.tf`.

```bash
az group create --name rg-fiap-feedback-tfstate --location brazilsouth
az storage account create --name stfiaptfstatefm02 --resource-group rg-fiap-feedback-tfstate --location brazilsouth --sku Standard_LRS --min-tls-version TLS1_2 --allow-blob-public-access false
az storage container create --account-name stfiaptfstatefm02 --name tfstate --auth-mode key --public-access off
az role assignment create --assignee <seu-object-id> --role "Storage Blob Data Contributor" --scope <id-da-conta-de-armazenamento>
```

### 3. Criação dos recursos

```bash
cd infra
terraform init
terraform plan -var="subscription_id=<id-da-assinatura>" -var="admin_email=<email>" -var="suffix=fmtc4"
terraform apply -var="subscription_id=<id-da-assinatura>" -var="admin_email=<email>" -var="suffix=fmtc4"
```

Variáveis:

- `subscription_id`: assinatura Azure
- `admin_email`: e-mail do administrador, que recebe notificações, relatório e alertas
- `suffix`: de 3 a 6 letras minúsculas ou números, usado em nomes que precisam ser únicos
- `function_app_name`: nome da Function App (padrão `fiap-feedback-fm-tc4`)

O Terraform cria, no grupo `rg-fiap-feedback-tf`:

- Storage Account, Log Analytics e Application Insights
- Cosmos DB (free tier), banco `feedback` e container `avaliacoes`
- Communication Services, Email Service e domínio gerenciado
- Plano Consumption e Function App (Java 21, Managed Identity)
- Permissão da Function App no Cosmos DB
- Action Group e alertas de exceções e de requisições com falha

O Cosmos DB free tier é limitado a uma conta por assinatura.

Para remover tudo:

```bash
terraform destroy -var="subscription_id=<id-da-assinatura>" -var="admin_email=<email>" -var="suffix=fmtc4"
```

---

## Deploy automatizado (GitHub Actions)

Workflow:

```text
.github/workflows/deploy.yml
```

A cada push na `main` que altere `feedback-functions/` (ou manualmente, em `Actions > Run workflow`), o workflow:

1. Instala o Java 21
2. Executa o build e os testes
3. Publica as funções na Function App `fiap-feedback-fm-tc4`

Configuração (uma única vez):

1. Gere o perfil de publicação (no PowerShell, `| Set-Clipboard` copia o conteúdo sem exibi-lo):

```bash
az functionapp deployment list-publishing-profiles -g rg-fiap-feedback-tf -n fiap-feedback-fm-tc4 --xml
```

2. No GitHub, abra `Settings > Secrets and variables > Actions > New repository secret`
3. Crie o secret `AZURE_FUNCTIONAPP_PUBLISH_PROFILE` com o conteúdo gerado

O Terraform não é executado pelo workflow: a infraestrutura é criada manualmente e o workflow publica apenas o código.

Deploy manual:

```bash
cd feedback-functions
./mvnw package
```

Em seguida, compacte o conteúdo de `target/azure-functions/fiap-feedback-fm-tc4` e publique:

```bash
az functionapp deployment source config-zip -g rg-fiap-feedback-tf -n fiap-feedback-fm-tc4 --src func.zip
```

---

## Monitoramento

- O Application Insights está conectado à Function App e registra logs, falhas e métricas.
- O alerta `alerta-excecoes` (severidade 1) dispara quando há exceções.
- O alerta `alerta-falhas-requisicao` (severidade 2) dispara quando há requisições com falha.
- Os alertas enviam e-mail ao administrador pelo Action Group `ag-admin-feedback-tf`.

Consulta útil em `Application Insights > Logs`:

```text
union traces, exceptions | order by timestamp desc
```

---

## Collection Postman

Arquivo:

```text
feedback.postman_collection.json
```

Importação:

1. Abra o Postman
2. Clique em `Import`
3. Selecione `feedback.postman_collection.json`
4. Preencha a variável `functionKey` da collection

A collection inclui avaliações de notas 2, 5 e 9, nota inválida, corpo sem descrição e chamada sem chave.

---

## Testes unitários

Para executar os testes e o build:

```bash
cd feedback-functions
./mvnw package
```

No Windows:

```bash
cd feedback-functions
mvnw.cmd package
```

Cobertura atual: 26 testes.

| Teste | Tipo | Cobertura |
|---|---|---|
| `DominioTest` | JUnit | Regra de urgência e cálculo do relatório |
| `ReceberAvaliacaoFunctionTest` | JUnit + Mockito | Validação, fila apenas para críticas, erros 400 e 500 |
| `NotificarCriticoFunctionTest` | JUnit + Mockito | Conteúdo do e-mail e falhas para nova tentativa |
| `RelatorioSemanalFunctionTest` | JUnit + Mockito | Relatório, janela de 7 dias e falha na consulta |
| `ReceberAvaliacaoQuarkusTest` | `@QuarkusTest` | Injeção de dependências do Quarkus com `@InjectMock` |
