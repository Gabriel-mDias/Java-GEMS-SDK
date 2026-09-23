# GEMS SDK Java — Guia de Consumo para IAs

## Compatibilidade 3.3.0

A release 3.3.0 e MINOR e aditiva. Grupos de primeiro nivel do realm passam a ter gateway proprio.
O contexto de auditoria continua opt-in e desligado por padrao. A SDK nao fornece auto-configuracao
ou credenciais administrativas Keycloak e preserva a superficie 3.2.0.

> Guia condensado e prático para geração de código seguro e aderente ao padrão GEMS.
> Para a referência completa da API pública (assinaturas, DTOs, snippets), veja [`llms.txt`](llms.txt).

---

## Quick Start

### 1. Adicionar o repositório

```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/Gabriel-mDias/Java-GEMS-SDK</url>
    </repository>
</repositories>
```

### 2. Importar o BOM (uma vez por projeto)

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>br.com.gems</groupId>
            <artifactId>gems-bom</artifactId>
            <version>3.3.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 3. Declarar os módulos desejados (sem `<version>`)

```xml
<dependency><groupId>br.com.gems</groupId><artifactId>gems-utils</artifactId></dependency>
<dependency><groupId>br.com.gems</groupId><artifactId>gems-exception</artifactId></dependency>
<dependency><groupId>br.com.gems</groupId><artifactId>gems-rest-common</artifactId></dependency>
<!-- 3.0.0 -->
<dependency><groupId>br.com.gems</groupId><artifactId>gems-mapstruct</artifactId></dependency>
<dependency><groupId>br.com.gems</groupId><artifactId>gems-auditing</artifactId></dependency>
<dependency><groupId>br.com.gems</groupId><artifactId>gems-keycloak-admin</artifactId></dependency>
<dependency><groupId>br.com.gems</groupId><artifactId>gems-security-authorization</artifactId></dependency>
<!-- adicione os demais conforme necessário -->
```

---

## Tabela de Referência Rápida

| Módulo | Dependência Maven | Flag de Ativação | Classe / Bean Principal |
|---|---|---|---|
| `gems-utils` | `gems-utils` | nenhuma | `DateUtil`, `DocumentoUtil`, `EmailUtil`, `ObjectUtil`, `UUIDUtil` |
| `gems-model-mapper` | `gems-model-mapper` | nenhuma (`@ConditionalOnMissingBean`) | `ModelMapper` (bean), `ModelMapperUtils` (static) — **suportado, não depreciado** |
| `gems-mapstruct` | `gems-mapstruct` | nenhuma (sem auto-config) | `GemsMappingConfig` — **recomendado para código novo** |
| `gems-exception` | `gems-exception` | nenhuma | `BusinessException`, `SecurityException`, `GlobalExceptionHandler` (400/403/502 + envelope uniforme) |
| `gems-jpa` | `gems-jpa` | `gems.jpa.enabled=true` | `BaseCustomJpaRepository<T>` |
| `gems-jpa-multi-tenant` | `gems-jpa-multi-tenant` | `gems.tenant.enabled=true` | `TenantScope`, `JpaTenantContext`, `TenantSchemaService` — **falha fechada desde a 3.0.0** |
| `gems-auditing` | `gems-auditing` | `gems.auditing.enabled=true` + `gems.auditing.schema` | `@Auditable`, `@SensitiveField`, `AuditActorProvider`, `AuditTrailDestination` |
| `gems-keycloak-admin` | `gems-keycloak-admin` | nenhuma (sem auto-config) | `KeycloakAdminGateway`, `KeycloakUserLifecycleGateway`, `KeycloakRealmRoleGateway`, `KeycloakRealmGroupGateway` |
| `gems-security-authorization` | `gems-security-authorization` | nenhuma (sem auto-config) | `AuthorizationCatalog`, `@PublicEndpoint`/`@GlobalEndpoint`/`@TenantEndpoint`, `TenantAuthorizationInterceptor`, `FrontendActionCatalogGenerator` |
| `gems-aws` | `gems-aws` | `aws.s3.enabled=true` | `S3Service` |
| `gems-aws-web` | `gems-aws-web` | `aws.s3.enabled=true` | `S3Controller` (automático) |
| `gems-rest-common` | `gems-rest-common` | `gems.rest.correlation-id.enabled=true` | `ApiResponseDTO`, `PageResponseDTO` |
| `gems-validation` | `gems-validation` | nenhuma | `@ValidCpf`, `@ValidCnpj`, `@ValidEmail` |
| `gems-openapi` | `gems-openapi` | `gems.openapi.enabled=true` | `OpenAPI` (bean automático) |
| `gems-observability` | `gems-observability` | `gems.observability.enabled=true` | `ObservedAspect` (bean automático) |

---

## Por Módulo

---

### gems-utils

Classes estáticas, sem Spring. Úteis em qualquer camada.

```java
// Validação de documentos
DocumentoUtil.isCpfValid("123.456.789-09");            // true/false
DocumentoUtil.isCnpjValid("11.222.333/0001-81");       // true/false

// Validação de e-mail
EmailUtil.isValid("user@exemplo.com");

// Null/empty check genérico (String, Collection, null)
ObjectUtil.isNullOrEmpty(obj);
ObjectUtil.isNotNullAndNotEmpty(obj);

// UUID seguro
UUID id = UUIDUtil.fromStringOrNull(request.getParam("id")); // null se inválido

// Data de nascimento no passado
DateUtil.isValidBirthDate(LocalDate.of(1990, 1, 1));
```

---

### gems-exception

Registrado automaticamente como `@RestControllerAdvice`. Apenas lance as exceções certas.

```java
// Regra de negócio violada → 400
throw new BusinessException("CPF já cadastrado");
throw new BusinessException(ErrorTypeEnum.ALERTA, "Prazo de pagamento próximo");

// Auth → 401
throw new SecurityException("Token expirado");
```

Corpo da resposta de erro:
```json
{
  "occurrenceTime": "2026-06-21T10:00:00",
  "errorType": "FALHA",
  "message": "CPF já cadastrado",
  "path": "/api/usuarios",
  "method": "POST"
}
```

---

### gems-rest-common

Envelopes padronizados para respostas REST.

```java
// Resposta simples
return ResponseEntity.ok(ApiResponseDTO.ok(dto));
return ResponseEntity.ok(ApiResponseDTO.ok(dto, "Criado com sucesso"));

// Resposta paginada
Page<UsuarioDTO> page = service.listar(pageable);
return ResponseEntity.ok(PageResponseDTO.from(page));
```

`CorrelationIdFilter` é registrado automaticamente: propaga `X-Correlation-Id` e insere no MDC.

---

### gems-validation

Constraints de Bean Validation. Null é considerado válido — combine com `@NotNull`.

```java
public class CadastroDTO {
    @NotNull @ValidCpf
    private String cpf;

    @NotNull @ValidCnpj
    private String cnpj;

    @NotNull @ValidEmail
    private String email;
}
```

---

### gems-jpa

Ativar: `gems.jpa.enabled=true` e `gems.jpa.base-packages=br.com.seuprojeto`.

O padrão correto é uma **única interface** que estende `JpaRepository` e `BaseCustomJpaRepository`, com métodos `default` públicos para buscas complexas e métodos `private` que constroem o HQL dinamicamente.

```java
@Repository
public interface PedidoRepository extends JpaRepository<Pedido, UUID>, BaseCustomJpaRepository<Pedido> {

    // Métodos derivados do Spring Data — declarados normalmente
    Optional<Pedido> findByClienteIdAndStatus(UUID clienteId, StatusPedidoEnum status);
    boolean existsByClienteIdAndDataCancelamentoIsNull(UUID clienteId);

    // Ponto de entrada público — monta o Page a partir de count + query
    default Page<PedidoResponseDTO> search(PedidoFilterParams filterParams, Pageable pageable) {
        return new PageImpl<>(
            searchQuery(filterParams, pageable),
            pageable,
            countQuery(filterParams)
        );
    }

    private Long countQuery(PedidoFilterParams filterParams) {
        var hql = new StringBuilder();
        var params = new HashMap<String, Object>();
        hql.append(" SELECT count(p.id) FROM Pedido p JOIN p.cliente c ");
        appendFilters(filterParams, hql, params);
        return this.executeCountHql(hql, params);
    }

    private List<PedidoResponseDTO> searchQuery(PedidoFilterParams filterParams, Pageable pageable) {
        var hql = new StringBuilder();
        var params = new HashMap<String, Object>();
        hql.append(" SELECT new br.com.seuprojeto.dto.PedidoResponseDTO(p.id, c.id, c.nome, p.status) ");
        hql.append(" FROM Pedido p JOIN p.cliente c ");
        appendFilters(filterParams, hql, params);
        return this.executeHql(hql, params, pageable, PedidoResponseDTO.class);
    }

    private void appendFilters(PedidoFilterParams filterParams, StringBuilder hql, HashMap<String, Object> params) {
        hql.append(" WHERE 1=1 ");
        if (ObjectUtil.isNotNullAndNotEmpty(filterParams.getClienteNome())) {
            hql.append(" AND LOWER(c.nome) LIKE :clienteNome ");
            params.put("clienteNome", "%" + filterParams.getClienteNome().toLowerCase() + "%");
        }
        if (ObjectUtil.isNotNullAndNotEmpty(filterParams.getStatus())) {
            hql.append(" AND p.status = :status ");
            params.put("status", filterParams.getStatus());
        }
    }
}
```

No serviço, delegue ao `search` do repository e use `ObjectUtil` para guards antes de acessar o repositório:

```java
@Service
@RequiredArgsConstructor
public class PedidoService {

    private final ModelMapper mapper;
    private final PedidoRepository repository;

    public Page<PedidoResponseDTO> search(PedidoFilterParams filterParams, Pageable pageable) {
        return repository.search(filterParams, pageable);
    }

    public PedidoDTO findById(UUID id) {
        if (ObjectUtil.isNullOrEmpty(id)) {
            throw new BusinessException("O id do pedido não foi informado!");
        }
        Pedido entity = repository.findById(id)
            .orElseThrow(() -> new BusinessException("Pedido não encontrado!"));
        return mapper.map(entity, PedidoDTO.class);
    }
}
```

---

### gems-jpa-multi-tenant

Requer `gems-jpa`. Ativar ambos.

> **A 3.0.0 muda o comportamento deste módulo e quebra a API pública dele** — é a única razão de a versão ser MAJOR. Falha fechada é o padrão, o prefixo passou a ser `tenant_`, a migração roda no provisionamento, `JpaTenantContext` é `final` sem `DEFAULT_TENANT`, e `MultiTenantLiquibaseConfig` deixou de ser `@Component`. Se você está gerando código para um projeto que já usava a 2.0.x, leia "O que mudou na 3.0.0" do [`README.md`](README.md) e a seção de migração de [`RELEASE-NOTES-3.0.0.md`](RELEASE-NOTES-3.0.0.md) antes.

```yaml
gems:
  jpa:
    enabled: true
    base-packages: br.com.seuprojeto
  tenant:
    enabled: true
    schema-prefix: tenant_          # padrão desde a 3.0.0
    global-schema: administracao    # sem padrão; TenantScope.global() falha sem ela
    liquibase:
      changelog: db/changelog/changelog-multi-schemas.xml
```

**Entrar e sair de um tenant — use `TenantScope`:**

```java
// dado de uma organização
try (TenantScope escopo = TenantScope.forTenant("acme")) {
    repositorio.save(matricula);
}

// dado que não pertence a organização alguma — precisa ser EXPLÍCITO
try (TenantScope escopo = TenantScope.global()) {
    repositorio.save(parametroDeSistema);
}

// variantes funcionais
TenantScope.runInTenant("acme", () -> repositorio.save(matricula));
var todas = TenantScope.callInGlobal(organizacaoRepository::findAll);
```

`TenantScope` fecha mesmo que o corpo lance, e o fechamento **restaura o escopo anterior** em vez de apagar o contexto — o que faz o aninhamento funcionar.

**Padrão no Filter da aplicação consumidora** (caminho manual, preservado; só está correto por causa do `finally`):

```java
@Component
public class TenantFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        // extraia o tenant do JWT ou header e defina:
        JpaTenantContext.setCurrentTenant(tenant.toLowerCase());
        try {
            chain.doFilter(req, res);
        } finally {
            JpaTenantContext.clear(); // OBRIGATÓRIO
        }
    }
}
```

**Provisionamento de tenant** — é aqui que a migração roda; o caminho de persistência apenas verifica e recusa com `TenantSchemaNotReadyException`:

```java
@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final TenantSchemaService tenantSchemaService;

    public void registrar(String sigla) {
        tenantSchemaService.createSchemaAndRunLiquibase(sigla);
    }
}
```

---

### gems-mapstruct

**Recomendado para código novo.** `gems-model-mapper` continua suportado — não migre o que já funciona só por isso.

Sem auto-configuração e sem propriedade: o módulo é uma configuração de mapeamento compartilhada.

```java
@Mapper(config = GemsMappingConfig.class)
public interface OrganizacaoMapper {
    OrganizacaoResponse toResponse(Organizacao origem);
}
```

Convenção de pacote: `<dominio>/mapper/XxxMapper`. O bean é `componentModel = "spring"`, então é injetável direto.

> **`unmappedTargetPolicy = ERROR` não se relaxa.** Campo de destino sem origem **reprova a compilação**, nomeando o campo. Ao adotar num projeto existente a política vai reprovar builds — cada reprovação é um campo que hoje chega nulo em silêncio. Baixar para `WARN` na primeira dificuldade desmonta a razão de o módulo existir; a alternativa correta é tratar os campos, um a um.

---

### gems-auditing

Trilha de auditoria no nível do Hibernate. **Opt-in duas vezes**: a propriedade liga o módulo, e cada entidade escolhe participar.

```yaml
gems:
  auditing:
    enabled: true
    schema: auditoria    # OBRIGATÓRIA, sem padrão — a aplicação não sobe sem ela
```

```java
@Entity
@Auditable
public class Matricula {
    @SensitiveField           // registra QUE mudou, sem registrar os valores
    private String documento;
}
```

**O domínio nunca chama o escritor.** Um `Integrator` do Hibernate instala o listener; `TransactionalAuditWriter` é package-private de propósito, e um teste de arquitetura garante que nada fora do módulo o alcance. Se você está gerando código, **não** injete um serviço de auditoria num serviço de domínio — não existe um para injetar.

Dois pontos de extensão, ambos `@ConditionalOnMissingBean`:

| Bean | Padrão | Quando substituir |
| --- | --- | --- |
| `AuditActorProvider` | `SystemAuditActorProvider` (autor `SISTEMA`) | para registrar o usuário autenticado como autor |
| `AuditTrailDestination` | `FixedSchemaAuditTrailDestination`, lendo `gems.auditing.schema` | para rotear a trilha por organização |

---

### gems-keycloak-admin

Operações administrativas sobre o provedor de identidade, expressas como operação e não como REST do Keycloak. **Sem auto-configuração**: o consumidor constrói o bean.

```java
@Bean
KeycloakAdminGateway keycloakAdminGateway(/* seus @ConfigurationProperties, env var ou cofre */) {
    var propriedades = new KeycloakAdminProperties(
            baseUrl, realm, clientId, clientSecret,
            Duration.ofSeconds(5), Duration.ofSeconds(10));
    var rest = RestClient.builder().baseUrl(propriedades.baseUrl()).build();
    return new KeycloakAdminRestClient(rest, propriedades);
}
```

O token técnico é obtido por `client_credentials` e mantido em memória até perto do vencimento; um 401 invalida o cache e a chamada é repetida **uma única vez** — repetir indefinidamente transformaria credencial revogada em laço.

> **Nenhum campo tem valor padrão, e o segredo não existe em código.** `KeycloakAdminProperties` recusa `baseUrl`, `realm`, `clientId` ou `clientSecret` ausentes ou em branco **na construção**. Um padrão embutido é o que permite uma aplicação subir apontada para o realm errado sem que ninguém perceba. Nunca versione arquivo com o segredo.

Toda falha sai como `KeycloakAdminException` — e portanto como **502** no envelope do `gems-exception`.

Para perfis representados por grupos de primeiro nível, injete o mesmo cliente como
`KeycloakRealmGroupGateway`. `findRealmGroupByName` faz correspondência exata,
`createRealmGroup` tolera criação concorrente e `ensureRealmRoleOnRealmGroup` não remove
mapeamentos existentes.

---

### gems-security-authorization

Autorização por **ação concreta**, nunca por perfil genérico. **Sem auto-configuração**: o enum de ações é do consumidor.

```java
public enum AcaoDoMeuServico implements AuthorizationAction {
    ORGANIZACAO_CRIAR(GLOBAL),
    MATRICULA_EFETIVAR(TENANT);

    private final AuthorizationScope escopo;
    AcaoDoMeuServico(AuthorizationScope escopo) { this.escopo = escopo; }
    @Override public AuthorizationScope scope() { return escopo; }
    // name() vem do próprio enum — a interface não pede que ninguém o escreva
}

AuthorizationCatalog catalogo = AuthorizationCatalog.of(AcaoDoMeuServico.class);
```

Ação escrita errada **não compila** — é o ganho sobre manter um JSON em paridade à mão. Os catálogos global e tenant precisam ser disjuntos, e uma ação precisa casar `^[A-Z][A-Z0-9]*_[A-Z0-9_]+$` (`GESTOR`, sozinho, não passa).

**Declare a intenção de cada endpoint:**

| Anotação | Significado |
| --- | --- |
| `@PublicEndpoint` | aberto, deliberadamente |
| `@GlobalEndpoint` | exige autenticação, fora de organização |
| `@TenantEndpoint` | exige organização comprovada no contexto |

`EndpointAuthorizationScan.assertProtected(mapping, catalogo)` lança `UnprotectedEndpointException` quando algum endpoint não está marcado — ligue-o a um teste e o build reprova, em vez de haver um default silencioso. (`scan(...)` devolve a lista de `Violation` se você preferir relatar em vez de falhar.) `TenantAuthorizationInterceptor` **falha fechado**: autenticação que não implementa `AuthorizationContextAware`, autenticação ausente ou contexto sem alias recusam com **403** (não 401 — quem chegou ali está autenticado; o que falta é escopo).

**A lista de ações do frontend é gerada, não mantida:**

```java
FrontendActionCatalogGenerator.generate(AcaoDoMeuServico.class);        // devolve o JSON ordenado
FrontendActionCatalogGenerator.writeTo(caminho, AcaoDoMeuServico.class); // grava o arquivo
FrontendActionCatalogGenerator.verify(caminho, AcaoDoMeuServico.class);  // GeneratedCatalogOutOfDateException se divergir
```

Ligue o `verify` a um teste ou ao build. A partir daí a lista do frontend é **consequência** do enum, não uma terceira cópia a conferir.

---

### gems-aws / gems-aws-web

```yaml
aws:
  s3:
    enabled: true
    region: us-east-1
    bucket-name: meu-bucket
    # access-key / secret-key opcionais (usa IAM se omitidos)
```

```java
@Service
@RequiredArgsConstructor
public class DocumentoService {
    private final S3Service s3Service;

    public PresignedUrlResponseDTO gerarUrlUpload(String fileName, String contentType) {
        return s3Service.generatePresignedUploadUrl(
            GenerateUploadUrlRequestDTO.builder()
                .fileName(fileName)
                .contentType(contentType)
                .directory("documentos/")
                .build()
        );
    }

    public PresignedUrlResponseDTO gerarUrlDownload(String fileKey) {
        return s3Service.generatePresignedDownloadUrl(fileKey);
    }
}
```

Adicione `gems-aws-web` para expor os endpoints REST prontos em `/api/aws/s3`.

---

### gems-model-mapper

```java
// Mapeamento estrito (recomendado — evita erros de ambiguidade)
UsuarioDTO dto = ModelMapperUtils.mapStrict(entity, UsuarioDTO.class);

// Ou injete o bean ModelMapper para configurações customizadas
@RequiredArgsConstructor
public class MeuServico {
    private final ModelMapper modelMapper;
}
```

---

### gems-openapi

```yaml
gems:
  openapi:
    enabled: true
    title: "Minha API"
    description: "Descrição da API"
    version: "v1"
    contact-name: "Time GEMS"
    contact-email: "contato@exemplo.com"
```

Requer `springdoc-openapi-starter-webmvc-ui` no classpath. O bean `OpenAPI` é criado automaticamente.

---

### gems-observability

Requer `spring-boot-starter-actuator` (fornece `ObservationRegistry`).

```yaml
gems:
  observability:
    enabled: true
```

```java
@Observed(name = "usuario.buscar")
public Usuario buscarPorId(UUID id) { ... }
```

---

## Armadilhas Comuns

| Erro | Causa | Solução |
|---|---|---|
| `BeanDefinitionOverrideException` no JPA | `gems.jpa.enabled=true` conflitando com `@EnableJpaRepositories` próprio | Remova a anotação ou defina `spring.main.allow-bean-definition-overriding=true` |
| Tenant errado em threads assíncronas | `JpaTenantContext` não propagado para `@Async` | Propague manualmente via `TaskDecorator` ou use `CompletableFuture` dentro da thread com contexto já definido |
| `ModelMapper` ignorando campo | Ambiguidade no mapeamento | Use `ModelMapperUtils.mapStrict()` em vez do bean direto |
| S3 retornando 403 | Credenciais ausentes ou inválidas | Verifique `aws.s3.access-key` / `aws.s3.secret-key` ou permissões IAM |
| `@ValidCpf` aceitando `null` | Comportamento intencional da constraint | Adicione `@NotNull` ao campo |
| Versão desatualizada no módulo filho | Edição manual do `<version>` no pom.xml | Use `mvn versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false` |
| `TenantContextMissingException` onde antes funcionava | 3.0.0 passou a falhar fechado em vez de cair num schema padrão | Abra o escopo: `TenantScope.forTenant(alias)` para dado de organização, `TenantScope.global()` para o que não pertence a nenhuma |
| Dados gravados "somem" após o upgrade | O schema mudou de prefixo: o padrão passou de `instituicao_` para `tenant_` | Declare `gems.tenant.schema-prefix` com o valor que o seu banco já usa, ou migre os schemas |
| Escopo global falha ao abrir | `gems.tenant.global-schema` não configurada — não há padrão | Configure a propriedade; a SDK não escolhe um schema por omissão |
| `TenantSchemaNotReadyException` na primeira requisição | Desde a 3.0.0 a migração roda no **provisionamento**, não no primeiro uso | Chame `TenantSchemaService.createSchemaAndRunLiquibase(sigla)` ao provisionar a organização |
| Migração roda num schema e o tráfego lê de outro | Um `@Value("${gems.tenant.schema-prefix}")` próprio no projeto consumidor | Remova-o. `TenantSchemaNaming` é o único leitor da propriedade — era esse o defeito que a 3.0.0 fechou |
| Mapeador MapStruct não é gerado, sem erro algum | O pom declara `<annotationProcessorPaths>`, o que **substitui** a lista herdada | Acrescente lá `lombok-mapstruct-binding` (depois do Lombok) e `mapstruct-processor` (por último) |
| Build reprova em campo de destino sem origem | `unmappedTargetPolicy = ERROR` — é o propósito do `gems-mapstruct` | Trate o campo. **Não** baixe para `WARN`: cada reprovação é um campo que hoje chega nulo em silêncio |
| Aplicação não sobe: `gems.auditing.schema` não configurada | Obrigatória, sem padrão embutido — a SDK não escolhe o destino da trilha | Declare a propriedade, ou registre um `AuditTrailDestination` próprio para rotear por organização |
| Nada é auditado, sem erro | A trilha é opt-in duas vezes | `gems.auditing.enabled=true` **e** `@Auditable` na entidade |
| Não acho o serviço de auditoria para injetar | Não existe: o escritor é package-private de propósito (AU-6) | Marque a entidade com `@Auditable`; o listener do Hibernate cuida do resto |
| Endpoint recusado com 403 sem organização | `TenantAuthorizationInterceptor` falha fechado em `@TenantEndpoint` | Popule o contexto de organização. Não é 401: quem chegou ali está autenticado, o que falta é escopo |
| Aplicação não sobe apontada para o realm certo | `KeycloakAdminProperties` recusa campo obrigatório ausente na construção | Configure `baseUrl`, `realm`, `clientId` e `clientSecret`. Não há padrão embutido, e é deliberado |
| Lista de ações do frontend fora de sincronia | Ela é **gerada**, não mantida à mão | `FrontendActionCatalogGenerator.writeTo(...)`, com `verify(...)` num teste |

---

## Referência Completa

Para assinaturas completas, todos os DTOs, snippets de código e a lista completa de propriedades, consulte [`llms.txt`](llms.txt).
