# CLAUDE.md

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

Six independently-publishable modules, each a Spring Boot starter-style library that wires itself in via **Spring Boot auto-configuration** — consumers just add the dependency and (sometimes) set a property; no `@Import` or component scan needed. Auto-config classes are registered in each module's `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

Modules and their intra-SDK dependencies:
- `gems-bom` — `pom`-packaged Bill of Materials listing all `br.com.gems:*` modules at the project version, for consumers to `import`.
- `gems-utils` — pure utility classes (date, email, document/CPF-CNPJ, UUID, object). No Spring deps; foundation for others.
- `gems-model-mapper` — single auto-configured `ModelMapper` bean (`GemsModelMapperAutoConfiguration`, `@ConditionalOnMissingBean`). data-jpa is `optional`.
- `gems-exception` — global REST exception handling; web + Spring Security are `optional`.
- `gems-jpa` — base JPA repository abstractions (`BaseCustomJpaRepository` + impl). Auto-config gated on `gems.jpa.enabled=true`.
- `gems-jpa-multi-tenant` — depends on `gems-jpa`; schema-based multi-tenancy engine. Tenant identifiers are validated via `TenantIdentifierValidator` before composing schema SQL (SQL-injection guard).
- `gems-aws` — depends on `gems-utils`/`gems-exception`; `S3Service` + S3 client beans only (no web). Gated on `aws.s3.enabled=true`.
- `gems-aws-web` — depends on `gems-aws`; the optional `S3Controller` REST endpoints, registered only in servlet web apps.
- `gems-rest-common` — `ApiResponseDTO`, `PageResponseDTO`, and a `CorrelationIdFilter` (auto-registered in servlet apps).
- `gems-validation` — `@ValidCpf`/`@ValidCnpj`/`@ValidEmail` Bean Validation constraints delegating to `gems-utils` (no auto-config needed — discovered via `@Constraint`).
- `gems-openapi` — springdoc auto-config building an `OpenAPI` bean from `gems.openapi.*`.
- `gems-observability` — registers a Micrometer `ObservedAspect` (enables `@Observed`), gated on an `ObservationRegistry` bean being present.

### Conditional activation

Heavier modules activate only when explicitly enabled via `@ConditionalOnProperty`, so adding the dependency is harmless until opted in. Activation flags: `gems.jpa.enabled`, `gems.tenant.enabled`, `aws.s3.enabled` (default off); `gems.rest.correlation-id.enabled`, `gems.openapi.enabled`, `gems.observability.enabled` (default on). When adding new opt-in behavior, follow this pattern rather than activating beans unconditionally, and register the config in the module's `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (every Spring module here is a proper `@AutoConfiguration`).

### Shared dependency versions

Third-party versions are centralized in the root `pom.xml` `<dependencyManagement>` / `<properties>` (`aws.java.sdk.version`, `modelmapper.version`, `springdoc.version`) — do not hardcode versions in module poms.

### Multi-tenant module specifics

Schema-per-tenant isolation built on Hibernate's `MultiTenantConnectionProvider` / `CurrentTenantIdentifierResolver`, wired through `HibernatePropertiesCustomizer`.
- `JpaTenantContext` holds the current tenant in a ThreadLocal. The consuming application is responsible for populating it (e.g. a `OncePerRequestFilter` reading a JWT claim) **and clearing it in a `finally` block** to avoid leaking tenant state across pooled threads.
- `TenantSchemaService.createSchemaAndRunLiquibase(sigla)` provisions a new schema at runtime and applies Liquibase migrations — used for tenant onboarding.
- Config properties live under `gems.tenant.*` and `gems.jpa.base-packages` (see README for the full list).

## Conventions

- Lombok is used throughout (annotation processor configured in the root pom); `@Slf4j`, `@RequiredArgsConstructor`, etc.
- Public API classes carry Portuguese Javadoc; match that style when adding to the public surface.
- Each module ships both `-sources` and `-javadoc` jars (configured in the root build), so keep public types documented.
- Tests use `spring-boot-starter-test` (JUnit 5 + Mockito), declared once in the root pom for all modules.
