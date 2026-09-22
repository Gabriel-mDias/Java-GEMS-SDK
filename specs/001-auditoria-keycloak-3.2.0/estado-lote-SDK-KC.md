# Estado — Lote SDK-KC, rodada 001

| Controle | Valor |
| :--- | :--- |
| Checkpoint | `SDK-KC` |
| Pré-requisitos | `SDK-AUD` verde em `7e8340c` |
| Política de sessão | `nova` |
| SHA anterior | `7e8340c19870c1e42dc4a065e0ec9553cf44fe39` |
| Estado inicial | `aberto` |
| Estado final | `verde` |
| Gate focado | `mvn -B -pl gems-keycloak-admin,gems-security-authorization -am test`, exit 0; 27 testes Keycloak e 25 authorization, zero falhas/erros/skips |
| Ponto de recuperação | `nenhum; correção final concluída, aguardando revisão do orquestrador` |
| Tier de capacidade | `executor especializado` |
| Esforço mínimo / risco | `medium`; API pública aditiva, compensação externa e semântica idempotente de roles |
| Decisão / comparação de custo | `delegar`; 3 tipos públicos, 10 operações e testes REST superam pacote, onboarding, revisão e novo gate |

## 1. Rollback deste lote

Na branch de trabalho, executar `git checkout -- gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminRestClient.java gems-keycloak-admin/src/test/java/br/com/gems/keycloak/admin/KeycloakAdminRestClientTest.java`; remover individualmente com `Remove-Item -Force` os quatro novos arquivos listados no pacote, se existirem.

## 2. Concluído

| Tarefa | O que provou |
| :-- | :--- |
| T012 | `mvn -B -pl gems-keycloak-admin -am -Dtest=KeycloakPublicSurfaceTest '-Dsurefire.failIfNoSpecifiedTests=false' test`, exit 1: `ClassNotFoundException` para `KeycloakUserSnapshot` (vermelho esperado). |
| T013 | `KeycloakUserSnapshot` criado com validação e cópias defensivas de atributos e grupos. |
| T014 | `KeycloakUserLifecycleGateway` criado com as nove operações contratadas. |
| T015 | `KeycloakRealmRoleGateway` criado com as duas operações contratadas. |
| T016–T020 | Provas REST escritas antes da implementação; vermelho registrado por símbolos ausentes e verde focal posterior: 11 testes, zero falhas/erros/skips. |
| T021 | Rede de contrato de composição criada antes do fechamento; verde no gate focado (1 teste). |
| Reabertura | Vermelhos registrados para corpo nulo e conflito 409 em POST de composição; corrigidos. Prova focal: 22 testes Keycloak, zero falhas/erros/skips. |
| Correção final | Vermelho registrado para resposta nula legada de grupos e retorno de 409 composto; `ensureGroup` preserva criação e 409 retorna 0. Prova focal: 21 testes Keycloak, zero falhas/erros/skips. |

## 3. Em curso

- **Tarefa:** nenhuma
- **Arquivo:** não se aplica.
- **O que já está feito nele:** tolerância legada e caminho estrito de lifecycle estão separados; a semântica de 409 foi corrigida.
- **O que falta:** nada do executor; revisão e aceite pertencem ao orquestrador.

## 4. Não iniciado

Nenhuma nova tarefa de pacote; reabertura limitada às provas e correções solicitadas.

## 5. Arquivos tocados

`git status --porcelain --untracked-files=all`

```text
 M gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakAdminRestClient.java
 M gems-keycloak-admin/src/test/java/br/com/gems/keycloak/admin/KeycloakAdminRestClientTest.java
?? gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakRealmRoleGateway.java
?? gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakUserLifecycleGateway.java
?? gems-keycloak-admin/src/main/java/br/com/gems/keycloak/admin/KeycloakUserSnapshot.java
?? gems-keycloak-admin/src/test/java/br/com/gems/keycloak/admin/KeycloakPublicSurfaceTest.java
?? gems-security-authorization/src/test/java/br/com/gems/security/authorization/KeycloakRoleCompositionContractTest.java
?? specs/001-auditoria-keycloak-3.2.0/pacote-de-execucao-SDK-KC.md
```

## 6. Decisões fora do pacote

Nenhuma.

## 7. O que está quebrado agora

Nada — gate focal e gate focado estão verdes. `git diff --check` teve exit 0; persistem somente avisos LF→CRLF dos dois arquivos rastreados.

## 8. Sessão anterior

- **Executor:** Codex
- **Identificador anterior:** não se aplica; política `nova`.
