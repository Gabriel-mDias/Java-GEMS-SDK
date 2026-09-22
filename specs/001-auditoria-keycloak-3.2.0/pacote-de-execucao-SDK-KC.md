# Pacote de Execução — Rodada 001, SDK-KC

| Controle | Valor |
| :--- | :--- |
| Checkpoint | `SDK-KC` |
| Pré-requisitos | `SDK-AUD` verde em `7e8340c` |
| Política de sessão | `nova` |
| SHA anterior | `7e8340c19870c1e42dc4a065e0ec9553cf44fe39` |
| Estado inicial | `aberto` |
| Âncora pre-dispatch | `git status --porcelain --untracked-files=all` sem saída antes da criação deste pacote |
| Gate focado | raiz; `mvn -B -pl gems-keycloak-admin,gems-security-authorization -am test` |
| Tier de capacidade | executor especializado |
| Esforço mínimo | `medium`; dez tarefas coesas, transporte REST localizado e duas redes de contrato |
| Risco | alto — API pública aditiva, compensação externa e semântica idempotente de roles |
| Decisão | delegar |
| Comparação de custo | 3 tipos públicos, 10 operações de lifecycle/roles e testes REST superam pacote, onboarding, revisão e novo gate |

> A lista `READ` é fechada. Nada mais pode ser lido ou pesquisado. Se faltar contexto, pare e
> reporte. Não comite. Antes da primeira edição, crie e preencha a §1 de
> `estado-lote-SDK-KC.md`; atualize-o a cada fronteira de tarefa. Rode o portão da §5.

## 1. Fronteira fechada do lote

Implementar somente T012–T021 do contrato Keycloak 3.2.0. Não executar T022/mutações finais,
não iniciar SDK-REL, não alterar POM, versão, contrato, tasks, roteiro ou documentação.

### READ

- `specs/001-auditoria-keycloak-3.2.0/pacote-de-execucao-SDK-KC.md`
- `specs/001-auditoria-keycloak-3.2.0/contracts/keycloak-admin-3.2.0.md`
- `CLAUDE.md`
- `gems-keycloak-admin/pom.xml`
- `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminException.java`
- `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminGateway.java`
- `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminProperties.java`
- `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminRestClient.java`
- `gems-keycloak-admin/src/test/java/br/com/gems/keycloak/admin/CredentialSourceTest.java`
- `gems-keycloak-admin/src/test/java/br/com/gems/keycloak/admin/KeycloakAdminRestClientTest.java`
- `gems-security-authorization/pom.xml`
- `gems-security-authorization/src/main/java/br/com/gems/security/authorization/AuthorizationAction.java`
- `gems-security-authorization/src/main/java/br/com/gems/security/authorization/AuthorizationCatalog.java`
- `gems-security-authorization/src/main/java/br/com/gems/security/authorization/AuthorizationScope.java`
- `gems-security-authorization/src/test/java/br/com/gems/security/authorization/AcaoDeTeste.java`
- `gems-security-authorization/src/test/java/br/com/gems/security/authorization/AuthorizationCatalogTest.java`
- `D:/repos/Meduc/meduc-workspace/.agents/skills/model-delegation/references/package-contract.md`
- `D:/repos/Meduc/meduc-workspace/.specify/templates/estado-lote-template.md`

### Regras e instruções consumidas

- `CLAUDE.md` — instrução local; o repositório não possui `AGENTS.md`, constituição ou regras `.gems-ai`.

> Nada fora de READ pode ser pesquisado ou lido. Precisou de outro arquivo, pare e reporte.

### EDIT / CREATE

| # | Ação | Arquivo |
| :-- | :--- | :--- |
| T012 | CREATE | `gems-keycloak-admin/src/test/java/br/com/gems/keycloak/admin/KeycloakPublicSurfaceTest.java` |
| T013 | CREATE | `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakUserSnapshot.java` |
| T014 | CREATE | `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakUserLifecycleGateway.java` |
| T015 | CREATE | `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakRealmRoleGateway.java` |
| T016–T020 | EDIT | `gems-keycloak-admin/src/test/java/br/com/gems/keycloak/admin/KeycloakAdminRestClientTest.java` `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminRestClient.java` |
| T021 | CREATE | `gems-security-authorization/src/test/java/br/com/gems/security/authorization/KeycloakRoleCompositionContractTest.java` |
| SDK-KC | CREATE | `specs/001-auditoria-keycloak-3.2.0/estado-lote-SDK-KC.md` |

## 2. O que já existe e você consome

`KeycloakAdminGateway` fica inalterado. Implemente exatamente os três tipos públicos do contrato:
`KeycloakUserSnapshot`, `KeycloakUserLifecycleGateway` e `KeycloakRealmRoleGateway`.
`KeycloakAdminRestClient` passa a implementar as três interfaces. Snapshot inclui dados escalares,
atributos imutáveis e ids imutáveis de grupos. `restoreUser` não recria usuário apagado.

`createUser` preserva assinatura; se reset-password falhar, faz DELETE do usuário criado. Falha do
DELETE fica suprimida na falha principal. Toda falha externa continua `KeycloakAdminException`
sanitizada. Join/leave e criação de role são idempotentes. Composta acrescenta somente filhos
diretos ausentes, preserva extras e retorna quantos acrescentou.

## 3. Arquivos de referência — copie destes

| Para escrever | Copie de |
| :--- | :--- |
| API pública e Javadoc | `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminGateway.java` |
| transporte e sanitização | `gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminRestClient.java` |
| teste REST | `gems-keycloak-admin/src/test/java/br/com/gems/keycloak/admin/KeycloakAdminRestClientTest.java` |
| catálogo tipado | `gems-security-authorization/src/main/java/br/com/gems/security/authorization/AuthorizationCatalog.java` |

## 4. As regras que valem para este lote

1. T012 e T016–T021 são test-first; registre o vermelho antes da implementação correspondente.
2. `KeycloakAdminGateway`, properties, exception e POMs ficam inalterados.
3. Tipos públicos novos têm Javadoc pt-BR e não expõem tipos do cliente Keycloak.
4. Snapshot faz cópia profunda de atributos/listas e cópia imutável de grupos; valida campos obrigatórios.
5. Ausência é `Optional.empty` em find e exceção sanitizada em snapshot/update/delete.
6. Compensação nunca mascara a falha primária; a falha do DELETE entra em `addSuppressed`.
7. Join/leave repetidos não falham; conflito concorrente de criação de role é sucesso idempotente.
8. Composição consulta filhos diretos, não roles efetivas; não remove extras.
9. Não criar auto-configuração, credencial, dependência entre os módulos ou mensagem com corpo externo.
10. Não editar fora da tabela; contexto ausente exige parada, não descoberta de escopo.

## 5. O portão

Na raiz:

```powershell
mvn -B -pl gems-keycloak-admin,gems-security-authorization -am test
```

Esperado: exit `0`, zero falhas/erros/skips; baseline agregado de 100 testes, com Keycloak >= 10 e
authorization >= 24, e contagens somente crescentes. Executar também `git diff --check`.

## 6. O relatório

Relate: arquivos; feito/não feito; vermelhos test-first; comandos, exit e contagens por módulo;
divergências pacote×código. O relatório não substitui o estado persistido.
