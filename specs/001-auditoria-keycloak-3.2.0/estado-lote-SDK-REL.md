# Estado - Lote SDK-REL, rodada 001

| Controle | Valor |
| :--- | :--- |
| Checkpoint | `SDK-REL` |
| Politica de sessao | `nova` |
| Estado inicial | `em recuperacao` |
| Estado final | `verde` |
| Gate focado | reator 212/0/0/0; smokes 3.1.0 e 3.2.0 verdes; dez mutacoes vermelhas/restauradas; diff verde |
| Ponto de recuperacao | `nenhum` |

## 1. Rollback deste lote

Restaurar individualmente os arquivos listados no pacote; nao usar checkout/reset/clean amplo.

## 2. Concluido

| Tarefa | O que provou |
| :-- | :--- |
| T024-reparo | README (2 BOMs), AI guide (1 BOM), llms (`Current version` e BOM) apontam 3.2.0; historico 3.1.0 preservado. Release notes agora nomeiam APIs, propriedade `gems.auditing.context-columns-enabled` e migracao. |
| T026-1 | `TransactionalAuditWriter` `ID_ACTOR` -> `ID_ACTOR_X`; `AuditWithoutDomainCallTest`, exit 1; patch inverso restaurou. |
| T026-2 | `CD_CORRELATION` -> `CD_CORRELATION_X`; `AuditWithoutDomainCallTest`, exit 1; patch inverso restaurou. |
| T026-3 | `createUser` -> `createUserX`; `mvn -B -pl gems-keycloak-admin -am -DskipTests compile`, exit 1; restaurado. |
| T026-4 | `findUserById` -> `findUserByIdX`; mesmo gate, exit 1; restaurado. |
| T026-5 | `snapshotUser` -> `snapshotUserX`; mesmo gate, exit 1; restaurado. |
| T026-6 | `setUserEnabled` -> `setUserEnabledX`; mesmo gate, exit 1; restaurado. |
| T026-7 | `deleteUser` -> `deleteUserX`; mesmo gate, exit 1; restaurado. |
| T026-8 | `joinRealmGroup` -> `joinRealmGroupX`; mesmo gate, exit 1; restaurado. |
| T026-9 | `leaveRealmGroup` -> `leaveRealmGroupX`; mesmo gate, exit 1; restaurado. |
| T026-10 | `restoreUser` -> `restoreUserX`; mesmo gate, exit 1; restaurado. |
| Revisao | Orquestrador repetiu `mvn -B clean install -DgenerateBackupPoms=false`: 212 testes, zero falhas/erros/skips; smoke via BOM 3.2.0 e `git diff --check` sairam 0. |
| T027 | Commit `509db83`; PR #35; CI `35714217338` e CodeQL `35714217357` verdes; merge `705a6a3`; publish `35714599184` verde. |
| T028 | Cache Maven vazio resolveu BOM, auditing, Keycloak Admin e authorization 3.2.0 em `maven.pkg.github.com/Gabriel-mDias/Java-GEMS-SDK`; compile exit 0. |

## 3. Em curso

- **Tarefa:** nenhuma
- **Arquivo:** nao aplicavel
- **O que ja esta feito nele:** dez mutacoes vermelhas, cada uma restaurada por patch inverso; T024 reparada.
- **O que falta:** nada neste checkpoint; C11 esta liberado.

## 4. Nao iniciado

Nenhuma tarefa do executor.

## 5. Arquivos tocados

`git status --porcelain --untracked-files=all` deve conter somente os arquivos previstos no pacote e estado.

## 6. Decisoes fora do pacote

O verificador compartilhado `verificar-lote.ps1` foi executado, mas seu contrato exige pelo menos
um `AGENTS.md` e uma regra `.gems-ai/rules/*.md`. Este repositorio nao possui esses arquivos; a
incompatibilidade estrutural foi mantida visivel e a extensao foi conferida manualmente contra
READ/EDIT/CREATE, estado e diff real, sem inventar arquivos de governanca.

## 7. O que esta quebrado agora

Nada conhecido; todas as mutacoes foram restauradas.

## 8. Sessao anterior

- **Executor:** Codex
- **Identificador anterior:** nao se aplica; politica `nova`.
