# Java GEMS SDK

Este é o SDK Oficial do projeto GEMS contendo os recursos compartilhados utilizados pelos microsserviços.

## Módulos Disponíveis

- **`gems-utils`**: Classes utilitárias puras de data, e-mail, documentos, etc.
- **`gems-model-mapper`**: Configuração e integração do ModelMapper.
- **`gems-exception`**: Configuração Global de Exceções (`GlobalExceptionHandler` e suporte a Spring Security de forma opcional).
- **`gems-jpa`**: Classes bases para repositório (`BaseCustomJpaRepository`).
- **`gems-jpa-multi-tenant`**: Motor de multi-tenancy baseado em Schemas e provedores de conexão customizados do Hibernate.
- **`gems-aws`**: Conectividade simplificada com a AWS (S3 e Geração de Presigned URLs).

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
