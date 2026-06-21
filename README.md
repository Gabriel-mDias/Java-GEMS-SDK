# Java GEMS SDK

Este é o SDK Oficial do projeto GEMS contendo os recursos compartilhados utilizados pelos microsserviços.

## 🤖 Uso para IAs (Claude, Cursor, Copilot, Gemini)

Construímos um ambiente amigável para modelos de inteligência artificial gerarem código seguro e aderente ao padrão GEMS.

Se você está usando uma IA no seu projeto consumidor ou se você é uma IA lendo isso, leia os seguintes documentos:

- [`llms.txt`](llms.txt): Contexto completo de IA com as Regras de Ouro, assinaturas exatas de todas as classes e métodos públicos, outputs e APIs completas de todos os módulos.
- [`AI-CONSUMER-GUIDE.md`](AI-CONSUMER-GUIDE.md): Guia condensado e prático com quick start, tabela de referência rápida, exemplos mínimos por módulo e armadilhas comuns.

## Módulos Disponíveis

- **`gems-bom`**: Bill of Materials para alinhar as versões de todos os módulos da SDK nos projetos consumidores.
- **`gems-utils`**: Classes utilitárias puras de data, e-mail, documentos, etc.
- **`gems-model-mapper`**: Configuração e integração do ModelMapper.
- **`gems-exception`**: Configuração Global de Exceções (`GlobalExceptionHandler` e suporte a Spring Security de forma opcional).
- **`gems-jpa`**: Classes bases para repositório (`BaseCustomJpaRepository`). Auto-configuração ativável por `gems.jpa.enabled=true`.
- **`gems-jpa-multi-tenant`**: Motor de multi-tenancy baseado em Schemas e provedores de conexão customizados do Hibernate.
- **`gems-aws`**: Conectividade simplificada com a AWS (S3 e Geração de Presigned URLs) — apenas o serviço, sem stack web. Ativável por `aws.s3.enabled=true`.
- **`gems-aws-web`**: Endpoints REST opcionais (`S3Controller`) para o módulo AWS. Inclua-o apenas se quiser os endpoints prontos.
- **`gems-rest-common`**: Envelopes de resposta (`ApiResponseDTO`), paginação (`PageResponseDTO`) e filtro de correlation-id (`X-Correlation-Id`) para logs.
- **`gems-validation`**: Constraints de Bean Validation para documentos brasileiros e e-mail (`@ValidCpf`, `@ValidCnpj`, `@ValidEmail`).
- **`gems-openapi`**: Auto-configuração do springdoc-openapi (Swagger UI) com metadados padronizados via `gems.openapi.*`.
- **`gems-observability`**: Habilita a anotação `@Observed` (Micrometer) registrando um `ObservedAspect`. Requer um `ObservationRegistry` (ex.: Actuator) no contexto.

### Flags de ativação (resumo)

| Módulo | Flag | Default |
| --- | --- | --- |
| `gems-jpa` | `gems.jpa.enabled` | `false` (evita conflito com `@EnableJpaRepositories` próprio) |
| `gems-jpa-multi-tenant` | `gems.tenant.enabled` | `false` |
| `gems-aws` / `gems-aws-web` | `aws.s3.enabled` | `false` |
| `gems-rest-common` (correlation-id) | `gems.rest.correlation-id.enabled` | `true` |
| `gems-openapi` | `gems.openapi.enabled` | `true` |
| `gems-observability` | `gems.observability.enabled` | `true` |

> **Starters web opcionais:** em `gems-exception`, `gems-aws-web` e `gems-rest-common`, o `spring-boot-starter-web` é declarado como `optional`. A aplicação consumidora deve fornecer a stack web (o que já é o caso em qualquer microsserviço REST).

### Importando via BOM

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>br.com.gems</groupId>
            <artifactId>gems-bom</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```
Com o BOM importado, declare os módulos desejados sem precisar repetir a `<version>`.

## Como Importar

Adicione o repositório do GitHub Packages ao seu `pom.xml` ou `settings.xml` da máquina:
```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/Gabriel-mDias/Java-GEMS-SDK</url>
    </repository>
</repositories>
```

Em seguida, importe qualquer pacote:
```xml
<dependency>
    <groupId>br.com.gems</groupId>
    <artifactId>gems-utils</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Módulo Multi-Tenant (`gems-jpa-multi-tenant`)

### Quando o Multi-Tenant é Necessário?
A abordagem Multi-Tenant isolando dados por **Schema** deve ser adotada apenas em microserviços cuja persistência de dados deva ser segregada rigidamente por cliente (instituição). Isso garante conformidade legal, alta segurança na segregação de dados e facilidade na execução de relatórios por cliente sem o risco de vazamentos.

**Não utilize este módulo** se a sua aplicação for apenas um serviço de infraestrutura compartilhada ou se seus dados não possuem divisão por cliente (ex: catálogos globais, dicionários de dados abertos). Nestes cenários, utilize apenas o `gems-jpa`.

### Como o Tenant é Identificado?
A biblioteca intercepta requisições ao banco e redireciona a conexão JDBC para o schema correspondente através da classe `JpaTenantContext`.

Para informar qual é o schema da requisição atual, você deve povoar o contexto previamente, por exemplo, através de um Interceptor ou um Filter de segurança. 
Abaixo um exemplo de como extrair a organização atual de um Token JWT do Keycloak e injetar no contexto:

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Object orgClaim = jwtToken.getToken().getClaim("organization");
            String tenantAcronym = extractTenantFromClaim(orgClaim);

            if (tenantAcronym != null && !tenantAcronym.isBlank()) {
                // Injeta o tenant atual para a thread que for executar o JPA
                JpaTenantContext.setCurrentTenant(tenantAcronym.toLowerCase());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // É essencial limpar o contexto no fim do request!
            JpaTenantContext.clear();
        }
    }
}
```

### Criando novos Schemas Dinamicamente
O SDK provê um serviço para ajudar no onboarding de novos tenants em tempo de execução: o `TenantSchemaService`. 
Você pode injetar este serviço para mandar o banco de dados criar fisicamente um schema novo baseado na sigla do cliente e imediatamente rodar as migrações (`liquibase`) dele.

```java
@Service
@RequiredArgsConstructor
public class MeuServicoDeOnboarding {
    private final TenantSchemaService tenantSchemaService;

    public void registrarNovoCliente(String sigla) {
        // ... sua lógica de negócio ...
        
        // Cria o schema (ex: instituicao_sigla) e aplica o liquibase
        tenantSchemaService.createSchemaAndRunLiquibase(sigla);
    }
}
```

### Propriedades de Configuração

```properties
# Habilitar o multi-tenant
gems.tenant.enabled=true

# Mapeamento do base-package do JPA para encontrar Entidades (default: br.com.gems)
gems.jpa.base-packages=br.com.seuprojeto

# Configurações do Schema
gems.tenant.schema-prefix=instituicao_
gems.tenant.client-query=SELECT DS_SIGLA_CLIENT FROM tenant_configuration.client
gems.tenant.liquibase.changelog=db/changelog/changelog-multi-schemas.xml
```

---

## Como Configurar um Novo Projeto Consumidor

Esta seção detalha todos os passos necessários para adicionar a GEMS SDK a um projeto Java do zero.

### 1. Configurar autenticação no GitHub Packages (uma vez por máquina)

A SDK é publicada no GitHub Packages, que exige autenticação mesmo para leitura. Edite (ou crie) o arquivo `~/.m2/settings.xml` na sua máquina:

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>SEU_USUARIO_GITHUB</username>
            <password>SEU_TOKEN_GITHUB</password>
        </server>
    </servers>
</settings>
```

> O token deve ter a permissão `read:packages`. Gere em: **GitHub → Settings → Developer settings → Personal access tokens**.

### 2. Adicionar o repositório no `pom.xml` do projeto

```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub Packages — GEMS SDK</name>
        <url>https://maven.pkg.github.com/Gabriel-mDias/Java-GEMS-SDK</url>
    </repository>
</repositories>
```

### 3. Importar o BOM para alinhar versões

Adicione em `<dependencyManagement>` — isso evita declarar `<version>` em cada módulo individualmente:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>br.com.gems</groupId>
            <artifactId>gems-bom</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 4. Declarar os módulos desejados (sem `<version>`)

```xml
<dependencies>
    <!-- Utilitários puros (data, documento, e-mail, UUID) -->
    <dependency>
        <groupId>br.com.gems</groupId>
        <artifactId>gems-utils</artifactId>
    </dependency>

    <!-- Tratamento global de exceções (@RestControllerAdvice automático) -->
    <dependency>
        <groupId>br.com.gems</groupId>
        <artifactId>gems-exception</artifactId>
    </dependency>

    <!-- Envelopes de resposta REST e filtro de Correlation-Id -->
    <dependency>
        <groupId>br.com.gems</groupId>
        <artifactId>gems-rest-common</artifactId>
    </dependency>

    <!-- Constraints de Bean Validation (@ValidCpf, @ValidCnpj, @ValidEmail) -->
    <dependency>
        <groupId>br.com.gems</groupId>
        <artifactId>gems-validation</artifactId>
    </dependency>

    <!-- ModelMapper pré-configurado -->
    <dependency>
        <groupId>br.com.gems</groupId>
        <artifactId>gems-model-mapper</artifactId>
    </dependency>

    <!-- Repositório JPA base (requer ativação via propriedade) -->
    <dependency>
        <groupId>br.com.gems</groupId>
        <artifactId>gems-jpa</artifactId>
    </dependency>

    <!-- Adicione os demais módulos conforme necessidade do projeto -->
</dependencies>
```

### 5. Configurar o `application.yml`

Abaixo estão **todas** as propriedades relevantes da SDK. Copie o bloco completo e remova as seções dos módulos que não estiver usando:

```yaml
spring:
  application:
    name: meu-servico

gems:
  # --- gems-jpa (obrigatório se usar gems-jpa) ---
  jpa:
    enabled: true                          # OBRIGATÓRIO — ativa o repositório base
    base-packages: br.com.seuprojeto      # pacote raiz das suas entidades/repositórios

  # --- gems-jpa-multi-tenant (somente se o serviço for multi-tenant) ---
  tenant:
    enabled: true
    schema-prefix: instituicao_           # prefixo dos schemas no banco
    liquibase:
      changelog: db/changelog/changelog-multi-schemas.xml

  # --- gems-rest-common (padrão: habilitado) ---
  rest:
    correlation-id:
      enabled: true                       # propaga X-Correlation-Id nos logs

  # --- gems-openapi (padrão: habilitado, requer springdoc no classpath) ---
  openapi:
    enabled: true
    title: "Meu Serviço"
    description: "Descrição do serviço"
    version: "v1"
    contact-name: "Time GEMS"
    contact-email: "gems@exemplo.com"

  # --- gems-observability (padrão: habilitado, requer Actuator) ---
  observability:
    enabled: true

# --- gems-aws / gems-aws-web ---
aws:
  s3:
    enabled: true                         # OBRIGATÓRIO para ativar o módulo AWS
    region: us-east-1                     # OBRIGATÓRIO
    bucket-name: meu-bucket               # OBRIGATÓRIO
    access-key: AKIAIOSFODNN7EXAMPLE      # opcional — omitir para usar IAM role
    secret-key: wJalrXUtnFEMI/K7MDENG    # opcional — omitir para usar IAM role
    endpoint-url: http://localhost:4566   # opcional — LocalStack/MinIO em dev
    presigned-url-expiration-minutes: 15  # opcional, padrão: 15
```

> **Módulos com ativação automática** (não precisam de propriedade): `gems-utils`, `gems-exception`, `gems-model-mapper`, `gems-validation`.

### 6. Implementar o `TenantFilter` (somente para serviços multi-tenant)

A SDK não sabe de onde vem o identificador do tenant — isso é responsabilidade da aplicação consumidora. O padrão recomendado é um `OncePerRequestFilter` que extrai o tenant do JWT e injeta no `JpaTenantContext`:

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Object orgClaim = jwtToken.getToken().getClaim("organization");
            String tenantAcronym = (orgClaim != null) ? orgClaim.toString() : null;

            if (tenantAcronym != null && !tenantAcronym.isBlank()) {
                JpaTenantContext.setCurrentTenant(tenantAcronym.toLowerCase());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            JpaTenantContext.clear(); // OBRIGATÓRIO — evita vazamento entre threads do pool
        }
    }
}
```

---

## Manutenção e Versionamento do SDK

Sempre que desejar realizar uma nova *release* do SDK ou evoluir a sua versão (ex: da versão `1.0.0` para a `1.1.0`), **não edite os arquivos `pom.xml` manualmente**, pois isso pode quebrar o elo de herança entre os submódulos do projeto e gerar falhas de build/compilação.

Utilize o comando oficial do plugin do Maven na raiz do projeto (onde fica o `Java-GEMS-SDK`):
```bash
mvn versions:set -DnewVersion=<NOVA_VERSAO> -DgenerateBackupPoms=false
```
Esse comando irá sincronizar simultaneamente a versão em todos os arquivos de configuração do SDK, mantendo a integridade da arquitetura de múltiplos módulos de forma perfeita.
