# Pacote de Execucao - Rodada 001, SDK-REL

| Controle | Valor |
| :--- | :--- |
| Checkpoint | `SDK-REL` |
| Pre-requisitos | `SDK-AUD` verde em `7e8340c`; `SDK-KC` verde em `1e56abc` |
| Politica de sessao | `nova` |
| SHA anterior | `1e56abc91c5c937c72a1b5d52cedbc6abaec8e4a` |
| Estado inicial | `aberto` |
| Ancora pre-dispatch | `specs/001-auditoria-keycloak-3.2.0/ancora-porcelain-SDK-REL.txt` |
| Gate focado | smoke 3.1.0; `mvn -B clean install -DgenerateBackupPoms=false`; `git diff --check` |
| Tier de capacidade | executor mecanico |
| Esforco minimo | `medium`; smoke, cinco documentos, 17 POMs e dez mutacoes |
| Risco | alto - compatibilidade, SemVer, BOM e release de dezessete artefatos |
| Decisao | delegar T023-T026; T027/T028 ficam com o orquestrador |
| Comparacao de custo | trabalho repetitivo de smoke, docs, POMs e mutacoes supera pacote, onboarding, revisao e repeticao dos gates |

> READ e fechado. Se faltar contexto, pare e reporte. Nao comite, envie branch, abra PR ou publique.
> Antes da primeira edicao, crie a secao 1 de `estado-lote-SDK-REL.md` e mantenha o estado por tarefa.

## 1. Fronteira fechada do lote

Execute apenas T023-T026. Nao altere contratos, spec, plan, research, quickstart, tasks ou roteiro.
Se o smoke quebrar por incompatibilidade 3.1.0, pare em `aguardando-decisao-major` sem acomodar.

### READ

- `specs/001-auditoria-keycloak-3.2.0/pacote-de-execucao-SDK-REL.md`
- `specs/001-auditoria-keycloak-3.2.0/ancora-porcelain-SDK-REL.txt`
- `specs/001-auditoria-keycloak-3.2.0/spec.md`
- `specs/001-auditoria-keycloak-3.2.0/quickstart.md`
- `specs/001-auditoria-keycloak-3.2.0/estado-lote-SDK-AUD.md`
- `specs/001-auditoria-keycloak-3.2.0/estado-lote-SDK-KC.md`
- `CLAUDE.md`
- `README.md`
- `AI-CONSUMER-GUIDE.md`
- `llms.txt`
- `RELEASE-NOTES-3.1.0.md`
- `pom.xml`
- `gems-bom/pom.xml`
- `.github/workflows/pr-validation.yml`
- `.github/workflows/publish.yml`
- `gems-auditing/src/main/java/br/com/gems/auditing/AuditActor.java`
- `gems-auditing/src/main/java/br/com/gems/auditing/AuditActorProvider.java`
- `gems-auditing/src/main/java/br/com/gems/auditing/AuditingAutoConfiguration.java`
- `gems-auditing/src/main/java/br/com/gems/auditing/TransactionalAuditWriter.java`
- `gems-auditing/src/main/java/br/com/gems/auditing/HibernateAuditListener.java`
- `gems-auditing/src/test/java/br/com/gems/auditing/AuditContextTest.java`
- `gems-auditing/src/test/java/br/com/gems/auditing/SuperficiePublicaTest.java`
- `gems-auditing/src/test/java/br/com/gems/auditing/TransactionalAuditWriterTest.java`
- `gems-auditing/src/test/java/br/com/gems/auditing/AuditWithoutDomainCallTest.java`
- `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminGateway.java`
- `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminProperties.java`
- `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminRestClient.java`
- `gems-keycloak-admin/src/test/java/br/com/gems/keycloak/admin/KeycloakAdminRestClientTest.java`
- `gems-keycloak-admin/src/test/java/br/com/gems/keycloak/admin/KeycloakPublicSurfaceTest.java`
- `gems-security-authorization/src/main/java/br/com/gems/security/authorization/AuthorizationAction.java`
- `gems-security-authorization/src/main/java/br/com/gems/security/authorization/AuthorizationCatalog.java`
- `gems-security-authorization/src/test/java/br/com/gems/security/authorization/KeycloakRoleCompositionContractTest.java`
- `D:/repos/Meduc/meduc-workspace/.agents/skills/model-delegation/references/package-contract.md`
- `D:/repos/Meduc/meduc-workspace/.specify/templates/estado-lote-template.md`

### Regras e instrucoes consumidas

- `CLAUDE.md`

> O repositorio nao possui `AGENTS.md`, constituicao ou `.gems-ai/rules/`; nao invente regra externa.

### EDIT / CREATE

| # | Acao | Arquivo |
| :-- | :--- | :--- |
| T023 | CREATE | `specs/001-auditoria-keycloak-3.2.0/consumer-smoke-3.1.0/pom.xml` |
| T023 | CREATE | `specs/001-auditoria-keycloak-3.2.0/consumer-smoke-3.1.0/src/main/java/br/com/gems/smoke/Api310Smoke.java` |
| T024 | EDIT | `README.md` |
| T024 | EDIT | `AI-CONSUMER-GUIDE.md` |
| T024 | EDIT | `llms.txt` |
| T024 | EDIT | `CLAUDE.md` |
| T024 | CREATE | `RELEASE-NOTES-3.2.0.md` |
| T025 | EDIT | `pom.xml` |
| T025 | EDIT | `gems-auditing/pom.xml` |
| T025 | EDIT | `gems-aws/pom.xml` |
| T025 | EDIT | `gems-aws-web/pom.xml` |
| T025 | EDIT | `gems-bom/pom.xml` |
| T025 | EDIT | `gems-exception/pom.xml` |
| T025 | EDIT | `gems-jpa/pom.xml` |
| T025 | EDIT | `gems-jpa-multi-tenant/pom.xml` |
| T025 | EDIT | `gems-keycloak-admin/pom.xml` |
| T025 | EDIT | `gems-mapstruct/pom.xml` |
| T025 | EDIT | `gems-model-mapper/pom.xml` |
| T025 | EDIT | `gems-observability/pom.xml` |
| T025 | EDIT | `gems-openapi/pom.xml` |
| T025 | EDIT | `gems-rest-common/pom.xml` |
| T025 | EDIT | `gems-security-authorization/pom.xml` |
| T025 | EDIT | `gems-utils/pom.xml` |
| T025 | EDIT | `gems-validation/pom.xml` |
| T026 | EDIT/TEMPORARIO | `gems-auditing/src/main/java/br/com/gems/auditing/TransactionalAuditWriter.java` |
| T026 | EDIT/TEMPORARIO | `gems-auditing/src/main/java/br/com/gems/auditing/HibernateAuditListener.java` |
| T026 | EDIT/TEMPORARIO | `gems-auditing/src/main/java/br/com/gems/auditing/AuditingAutoConfiguration.java` |
| T026 | EDIT/TEMPORARIO | `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminRestClient.java` |
| SDK-REL | CREATE | `specs/001-auditoria-keycloak-3.2.0/estado-lote-SDK-REL.md` |

## 2. O que ja existe e voce consome

A branch parte de `1e56abc` e ainda declara versao 3.1.0. Antes do primeiro smoke, instale essa
propria arvore no repositorio Maven local com
`mvn -B install -DskipTests -DgenerateBackupPoms=false`; nao busque os artefatos no Maven Central.
O smoke congela apenas API 3.1.0: `AuditActor`,
`AuditActorProvider`, o overload de tres argumentos de
`AuditingAutoConfiguration#auditingHibernateCustomizer` de dois argumentos, `KeycloakAdminGateway` e
`KeycloakAdminProperties`. Ele fica fora do reator, importa `gems-bom:3.2.0`, declara
`gems-auditing`, `gems-keycloak-admin` e `gems-security-authorization` sem versao e usa Java 21. No
primeiro passe o BOM do smoke e 3.1.0; depois de T025, altere somente a versao do BOM do smoke para
3.2.0 e compile exatamente o mesmo `Api310Smoke.java`.

Documente 3.2.0 como MINOR aditiva: contexto de auditoria opt-in/desligado por padrao; lifecycle,
snapshot, grupos e roles em interfaces especializadas; sem credencial/autoconfig Keycloak; migracao
das duas colunas antes do opt-in. Preserve notas 3.0.0/3.1.0. Use apenas `mvn versions:set` no bump.

## 3. Arquivos de referencia - copie destes

| Para escrever | Copie de |
| :--- | :--- |
| smoke Maven/BOM | `README.md` e `AI-CONSUMER-GUIDE.md` |
| superficie 3.1.0 | os cinco arquivos publicos nomeados na secao 2 |
| release notes | `RELEASE-NOTES-3.1.0.md` |
| mutacoes | estados SDK-AUD/SDK-KC e testes listados em READ |

## 4. As regras que valem para este lote

1. Instale a arvore ainda em 3.1.0 sem testes, crie o smoke antes do bump e compile contra 3.1.0.
2. Depois do bump/install, mude somente o BOM do POM do smoke para 3.2.0 e compile a mesma fonte contra 3.2.0.
3. O smoke nao usa tipos 3.2.0 e nao entra no reator.
4. Execute literalmente `mvn versions:set -DnewVersion=3.2.0 -DgenerateBackupPoms=false`.
5. Confira os 17 POMs e ausencia de nova `pom.xml.versionsBackup`.
6. Reproduza, uma por vez, as dez mutacoes ja contratadas: id, correlacao, opt-in, atomicidade; create, enable, delete, snapshot, grupos e roles.
7. Cada mutacao deve ficar vermelha, ser restaurada por patch inverso e voltar a verde; fontes temporariamente mutados terminam sem diff.
8. Gate integral com pelo menos 186 testes e zero falhas/erros/skips.
9. Nao edite fora da tabela; contexto ausente exige parada.

## 5. O portao

```powershell
mvn -B clean install -DgenerateBackupPoms=false
mvn -B -f specs/001-auditoria-keycloak-3.2.0/consumer-smoke-3.1.0/pom.xml clean compile
git diff --check
```

Todos devem sair `0`; registre tambem o smoke contra 3.1.0 antes do bump, as dez mutacoes, as
contagens por modulo e a conferencia dos 17 POMs.

## 6. O relatorio

Relate arquivos, feito/nao feito, dois smokes, dez mutacoes, 17 POMs, comandos/exits/contagens e
divergencias pacote-codigo. O relatorio nao substitui o estado persistido.
