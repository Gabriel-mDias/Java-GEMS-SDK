# GEMS SDK Java — Guia de Consumo para IAs

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
            <version>2.0.0</version>
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
<!-- adicione os demais conforme necessário -->
```

---

## Tabela de Referência Rápida

| Módulo | Dependência Maven | Flag de Ativação | Classe / Bean Principal |
|---|---|---|---|
| `gems-utils` | `gems-utils` | nenhuma | `DateUtil`, `DocumentoUtil`, `EmailUtil`, `ObjectUtil`, `UUIDUtil` |
| `gems-model-mapper` | `gems-model-mapper` | nenhuma (`@ConditionalOnMissingBean`) | `ModelMapper` (bean), `ModelMapperUtils` (static) |
| `gems-exception` | `gems-exception` | nenhuma | `BusinessException`, `SecurityException`, `GlobalExceptionHandler` |
| `gems-jpa` | `gems-jpa` | `gems.jpa.enabled=true` | `BaseCustomJpaRepository<T>` |
| `gems-jpa-multi-tenant` | `gems-jpa-multi-tenant` | `gems.tenant.enabled=true` | `JpaTenantContext`, `TenantSchemaService` |
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

```yaml
gems:
  jpa:
    enabled: true
    base-packages: br.com.seuprojeto
  tenant:
    enabled: true
    schema-prefix: instituicao_
    liquibase:
      changelog: db/changelog/changelog-multi-schemas.xml
```

**Padrão obrigatório no Filter da aplicação consumidora:**

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

**Provisionamento de tenant:**

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

---

## Referência Completa

Para assinaturas completas, todos os DTOs, snippets de código e a lista completa de propriedades, consulte [`llms.txt`](llms.txt).
