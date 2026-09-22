# CLAUDE.md

## Release 3.2.0

3.2.0 e uma evolucao MINOR aditiva: contexto de auditoria opt-in e desligado por padrao, com
migracao previa de `ID_ACTOR` e `CD_CORRELATION`; lifecycle, snapshots, grupos e roles Keycloak
ficam em interfaces especializadas. Nao adicionar auto-configuracao ou credenciais administrativas
Keycloak. A superficie 3.1.0 deve continuar compilando.

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

GEMS SDK — a multi-module Maven library (`br.com.gems:gems-sdk`) of shared, reusable resources consumed by GEMS microservices. It is published as JARs to GitHub Packages, not run as an application. Java 21, Spring Boot parent `4.1.0` (GA — no snapshot repos). Documentation and most code comments are in Portuguese (pt-BR).

## Build & test commands

```bash
# Full build + install all modules to local .m2 (this is what CI runs on PRs)
mvn --batch-mode clean install -DgenerateBackupPoms=false

# Build a single module along with its intra-SDK dependencies
mvn install -pl gems-aws -am

# Run all tests
mvn test

# Run tests for one module
mvn test -pl gems-utils

# Run a single test class / method
mvn test -pl gems-utils -Dtest=DateUtilTest
mvn test -pl gems-utils -Dtest=DateUtilTest#methodName
```

## Versioning & releasing (important)

- **Never edit `<version>` in any `pom.xml` by hand.** All submodules inherit the version from the root parent; hand-editing breaks the inheritance chain and the build. The publish workflow explicitly fails if parent and child versions are desynced.
- To bump the version, run from the repo root:
  ```bash
  mvn versions:set -DnewVersion=<NEW_VERSION> -DgenerateBackupPoms=false
  ```
- Releases are automatic: pushing to `main` triggers `.github/workflows/publish.yml`, which runs `mvn deploy -DskipTests` to GitHub Packages. PRs to `main` run `clean install` for validation.

## Architecture

Sixteen independently-publishable modules, most of them Spring Boot starter-style libraries that wire themselves in via **Spring Boot auto-configuration** — consumers just add the dependency and (sometimes) set a property; no `@Import` or component scan needed. Auto-config classes are registered in each module's `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Two modules are deliberately *not* auto-configured (`gems-keycloak-admin`, `gems-security-authorization`) — see below.

Modules and their intra-SDK dependencies:
- `gems-bom` — `pom`-packaged Bill of Materials listing all `br.com.gems:*` modules at the project version, for consumers to `import`.
- `gems-utils` — pure utility classes (date, email, document/CPF-CNPJ, UUID, object). No Spring deps; foundation for others.
- `gems-model-mapper` — single auto-configured `ModelMapper` bean (`GemsModelMapperAutoConfiguration`, `@ConditionalOnMissingBean`). data-jpa is `optional`. **First-class and supported** — it is not deprecated and is not going away.
- `gems-mapstruct` — **the recommended mapping approach for new code** (3.0.0). A single shared `@MapperConfig`, `GemsMappingConfig`, with `componentModel = "spring"` and `unmappedTargetPolicy = ERROR`; consumer mappers declare `@Mapper(config = GemsMappingConfig.class)`. The `ERROR` policy is the point of the module: a target field with no source fails compilation naming the field. **Never relax it to `WARN`** — each failure is a field that silently arrives null today.
- `gems-exception` — global REST exception handling; web + Spring Security are `optional`. Since 3.0.0 it emits a uniform error envelope and covers 400 (validation), 403 (`AccessDeniedException`) and 502 (external service) alongside the pre-existing handlers. 3.1.0 adds 404 (`NoResourceFoundException`), 400 (`MethodArgumentTypeMismatchException` — names the parameter, never the value) and 409 (`ConflictException`, new), and removes the `SecurityExceptionHandler` method that mapped `AuthorizationDeniedException` to 401 — 403 comes from `AuthorizationExceptionHandler` alone. `path` is `getRequestURI()` everywhere. **Never** echo the requested path or a parameter value in an error message.
- `gems-jpa` — base JPA repository abstractions (`BaseCustomJpaRepository` + impl). Auto-config gated on `gems.jpa.enabled=true`.
- `gems-jpa-multi-tenant` — depends on `gems-jpa`; schema-based multi-tenancy engine. Tenant identifiers are validated via `TenantIdentifierValidator` before composing schema SQL (SQL-injection guard). **Behaviour and public API changed in 3.0.0 — see "Multi-tenant module specifics".**
- `gems-auditing` — Hibernate-level audit trail, opt-in per entity via `@Auditable` and gated on `gems.auditing.enabled=true`. The domain never calls the writer: a Hibernate `Integrator` installs the listener, and `TransactionalAuditWriter` is package-private on purpose. Two `@ConditionalOnMissingBean` extension points — `AuditActorProvider` (defaults to `SystemAuditActorProvider`, actor `SISTEMA`) and `AuditTrailDestination`. `@SensitiveField` records that a field changed without recording its values.
- `gems-keycloak-admin` — `KeycloakAdminGateway`, the admin operations against the identity provider expressed as operations ("ensure organization", "create user"), with `KeycloakAdminRestClient` as the transport. Every failure leaves as `KeycloakAdminException`, i.e. 502 in the `gems-exception` envelope. **No auto-configuration and no defaults**: `KeycloakAdminProperties` rejects a missing `baseUrl`, `realm`, `clientId` or `clientSecret` at construction, so a misconfigured environment fails at startup instead of pointing at the wrong realm in production. The consumer builds the bean and binds the values from its own config mechanism; no secret is ever committed here.
- `gems-security-authorization` — authorization by **concrete action**, never by generic role. `AuthorizationCatalog.of(<enum>)` derives the catalogue from an enum implementing `AuthorizationAction`, so a mistyped action does not compile; global and tenant scopes must be disjoint. `@PublicEndpoint`/`@GlobalEndpoint`/`@TenantEndpoint` mark intent, `EndpointAuthorizationScan` fails the build on an unmarked endpoint, and `TenantAuthorizationInterceptor` fails **closed** (403, not 401) when a tenant endpoint arrives without a proven organization. `FrontendActionCatalogGenerator` *generates* the frontend's JSON action list from the same enum and `verify(...)` fails when it is out of date — the list is a consequence of the enum, not a third copy to keep in parity by hand.
- `gems-aws` — depends on `gems-utils`/`gems-exception`; `S3Service` + S3 client beans only (no web). Gated on `aws.s3.enabled=true`.
- `gems-aws-web` — depends on `gems-aws`; the optional `S3Controller` REST endpoints, registered only in servlet web apps.
- `gems-rest-common` — `ApiResponseDTO`, `PageResponseDTO`, and a `CorrelationIdFilter` (auto-registered in servlet apps).
- `gems-validation` — `@ValidCpf`/`@ValidCnpj`/`@ValidEmail` Bean Validation constraints delegating to `gems-utils` (no auto-config needed — discovered via `@Constraint`).
- `gems-openapi` — springdoc auto-config building an `OpenAPI` bean from `gems.openapi.*`.
- `gems-observability` — registers a Micrometer `ObservedAspect` (enables `@Observed`), gated on an `ObservationRegistry` bean being present.

### Conditional activation

Heavier modules activate only when explicitly enabled via `@ConditionalOnProperty`, so adding the dependency is harmless until opted in. Activation flags: `gems.jpa.enabled`, `gems.tenant.enabled`, `gems.auditing.enabled`, `aws.s3.enabled` (default off); `gems.rest.correlation-id.enabled`, `gems.openapi.enabled`, `gems.observability.enabled` (default on). When adding new opt-in behavior, follow this pattern rather than activating beans unconditionally, and register the config in the module's `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

`gems-keycloak-admin` and `gems-security-authorization` are the exception: they ship **no** auto-configuration. Both need values the SDK must not guess (an admin credential; the consumer's own action enum), so the consumer declares the beans. That is the design, not an omission.

### Observability floor

The four modules added in 3.0.0 (`gems-mapstruct`, `gems-auditing`, `gems-keycloak-admin`, `gems-security-authorization`) must **not** depend on `gems-observability`, directly or transitively. Their observability floor is structured logging and nothing beyond it; metrics and tracing arrive when a consumer asks for them. An architecture test in the reactor enforces this.

### Structured logging convention

The floor above is only useful if every module writes the same shape. These modules log through SLF4J with **structured arguments, never MDC**: MDC leaks across threads and is silently empty in async paths, and a field that is sometimes there is a field nobody can filter on.

Three fields are mandatory on every log statement these modules emit:

| Field | Value |
| :--- | :--- |
| `organizacao` | the organization the operation belongs to. **Never omitted.** When the context is absent, it carries the sentinel `"<ausente>"` — an absent organization is itself the fact worth reading, and dropping the field makes the two cases indistinguishable in a log search |
| `causa` | what happened, as a stable identifier rather than a sentence, so it survives message rewording |
| `modulo` | the emitting module (`gems-auditing`, `gems-jpa-multi-tenant`, …) |

When `organizacao` is `"<ausente>"`, the statement also carries **which alias source was consulted** and **which point refused** — without those two, the log says something went wrong without saying where to look.

Level is `ERROR` for a refused tenant context and for a failed migration. Both are conditions under which the application is serving requests it cannot serve correctly; anything quieter turns them into noise that a dashboard filters out.

### Shared dependency versions

Third-party versions are centralized in the root `pom.xml` `<dependencyManagement>` / `<properties>` (`aws.java.sdk.version`, `modelmapper.version`, `springdoc.version`) — do not hardcode versions in module poms.

### Multi-tenant module specifics

Schema-per-tenant isolation built on Hibernate's `MultiTenantConnectionProvider` / `CurrentTenantIdentifierResolver`, wired through `HibernatePropertiesCustomizer`.

> **3.0.0 changes observable behaviour here, and breaks source compatibility. This module is the sole reason 3.0.0 is a MAJOR release** — everywhere else the release is strictly additive. The module had zero declared consumers in the 2.x range at release time (re-verified immediately before publishing), but the version states the contract, not the consumer count. Read this section before upgrading.
>
> The API breaks, and this is the one place in the SDK where it does: `JpaTenantContext` is now `final` with a private constructor; its `public static final String DEFAULT_TENANT` was **removed** (failing closed leaves no default to name); `MultiTenantLiquibaseConfig` takes four constructor arguments instead of one and is no longer a `@Component`. Code written against `2.0.x` of this module does not compile against `3.0.0`. The migration steps are in `RELEASE-NOTES-3.0.0.md`.

- **Fail-closed is the default (MT-1).** A persistence operation with no tenant in context now raises `TenantContextMissingException` instead of silently falling back to a default schema. Data that genuinely belongs to no organization must say so explicitly, via a global scope.
- **`TenantScope` is the supported way to enter and leave a tenant.** It closes in a `finally` even when the body throws, and closing **restores the previous scope** rather than clearing the context, so nesting a global scope inside a tenant operation does not strand the outer one. `JpaTenantContext.setCurrentTenant`/`clear` still exist and still leave the discipline to the caller.
- **One schema prefix, in one place.** `TenantSchemaNaming` is the only reader of `gems.tenant.schema-prefix`, and the default is **`tenant_`**. Before this release the property was read in three places under two different defaults (`instituicao_` and `client_tenant_`), so migrations ran against one schema while traffic read from another, with no error anywhere. Re-declaring your own `@Value` for this property reintroduces that defect; `SchemaNamingConsistencyTest` looks for exactly that.
- **Migration runs at provisioning, not on first use.** `TenantMigrationCoordinator` applies the migrations when the organization is provisioned; the persistence path only *checks* and refuses with `TenantSchemaNotReadyException`. Running DDL inside a request path would charge the first request of each organization for the migration and race between concurrent requests.
- `TenantSchemaService.createSchemaAndRunLiquibase(sigla)` provisions a new schema and applies Liquibase migrations — used for tenant onboarding.
- Config properties live under `gems.tenant.*` (`schema-prefix`, `global-schema`, `client-query.*`) and `gems.jpa.base-packages` (see README for the full list).

## Conventions

- Lombok is used throughout (annotation processor configured in the root pom); `@Slf4j`, `@RequiredArgsConstructor`, etc.
- Public API classes carry Portuguese Javadoc; match that style when adding to the public surface.
- Each module ships both `-sources` and `-javadoc` jars (configured in the root build), so keep public types documented.
- Tests use `spring-boot-starter-test` (JUnit 5 + Mockito), declared once in the root pom for all modules.
