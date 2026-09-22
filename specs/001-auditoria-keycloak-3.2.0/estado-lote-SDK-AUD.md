# Estado — Lote SDK-AUD, rodada 001

| Controle | Valor |
| :--- | :--- |
| Checkpoint | `SDK-AUD` |
| Pré-requisitos | `SDK-P00 aprovado em 8e43585` |
| Política de sessão | `nova` |
| SHA anterior | `8e43585914eec2371dfaff03c36fbca9abe66fba` |
| Estado inicial | `aberto` |
| Estado final | `verde` |
| Gate focado | `mvn -B -pl gems-auditing -am test; exit 0; 34 testes, 0 falhas/erros/skips; git diff --check exit 0.` |
| Ponto de recuperação | `nenhum` |
| Tier de capacidade | `executor especializado` |
| Esforço mínimo / risco | `medium; alto — API pública aditiva, compatibilidade de schema e atomicidade transacional.` |
| Decisão / comparação de custo | `delegar; 3 tipos públicos, 3 integrações, redes H2/ORM e 4 mutações superam pacote, onboarding, revisão e novo gate.` |

## 1. Rollback deste lote

Na branch de trabalho, executar `git checkout -- gems-auditing/src/test/java/br/com/gems/auditing/SuperficiePublicaTest.java gems-auditing/src/test/java/br/com/gems/auditing/TransactionalAuditWriterTest.java gems-auditing/src/test/java/br/com/gems/auditing/AuditWithoutDomainCallTest.java gems-auditing/src/main/java/br/com/gems/auditing/TransactionalAuditWriter.java gems-auditing/src/main/java/br/com/gems/auditing/HibernateAuditListener.java gems-auditing/src/main/java/br/com/gems/auditing/AuditingAutoConfiguration.java`; depois, remover individualmente `gems-auditing/src/test/java/br/com/gems/auditing/AuditContextTest.java`, `gems-auditing/src/main/java/br/com/gems/auditing/AuditContext.java`, `gems-auditing/src/main/java/br/com/gems/auditing/AuditContextProvider.java`, `gems-auditing/src/main/java/br/com/gems/auditing/EmptyAuditContextProvider.java` e `specs/001-auditoria-keycloak-3.2.0/estado-lote-SDK-AUD.md` se existirem. Não executar rollback amplo.

## 2. Concluído

| Tarefa | O que provou |
| :-- | :--- |
| T002 | `mvn -B -pl gems-auditing -am test` ficou vermelho em `SuperficiePublicaTest`: `AuditContext` e `AuditContextProvider` não existem. |
| T003 | `mvn -B -pl gems-auditing -am test` ficou vermelho em `AuditContextTest`: não existe `AuditContext`, incluindo `empty()` e a normalização. |
| T004 | `mvn -B -pl gems-auditing -am test` verde: 30 testes, 0 falhas/erros/skips; T002/T003 satisfeitas pelos três tipos públicos novos. |
| T005 | `mvn -B -pl gems-auditing -am test` ficou vermelho: inexistem o construtor booleano e o overload de `write` com `AuditContext`; a rede H2/ORM foi criada antes da implementação. |
| T006 | Implementado SQL 3.1.0 preservado no default e SQL com `ID_ACTOR`/`CD_CORRELATION` no opt-in; a prova integral aguarda T007 porque a rede ORM ainda não compila com o construtor novo do listener. |
| T007 | Listener recebe `AuditContextProvider`, consulta ator/contexto uma vez por operação auditável e repassa ambos ao writer; a configuração ainda usa o construtor anterior, a ser ajustada em T008. |
| T008 | `mvn -B -pl gems-auditing -am test` verde: 34 testes, 0 falhas/erros/skips; provider vazio condicional e propriedade com default `false` ligados à integração. |
| T009 | Quatro mutações isoladas ficaram vermelhas e foram restauradas por edição inversa; gate final verde e `git diff --check` sem erro. |

## 3. Em curso

- **Tarefa:** nenhuma
- **Arquivo:** não aplicável
- **O que já está feito nele:** T002–T009 concluídas; quatro mutações restauradas.
- **O que falta:** nada neste checkpoint.

## 4. Não iniciado

Nenhuma.

## 5. Arquivos tocados

`git status --porcelain --untracked-files=all`

```text
 M gems-auditing/src/main/java/br/com/gems/auditing/AuditingAutoConfiguration.java
 M gems-auditing/src/main/java/br/com/gems/auditing/HibernateAuditListener.java
 M gems-auditing/src/main/java/br/com/gems/auditing/TransactionalAuditWriter.java
 M gems-auditing/src/test/java/br/com/gems/auditing/AuditWithoutDomainCallTest.java
 M gems-auditing/src/test/java/br/com/gems/auditing/SuperficiePublicaTest.java
 M gems-auditing/src/test/java/br/com/gems/auditing/TransactionalAuditWriterTest.java
?? gems-auditing/src/main/java/br/com/gems/auditing/AuditContext.java
?? gems-auditing/src/main/java/br/com/gems/auditing/AuditContextProvider.java
?? gems-auditing/src/main/java/br/com/gems/auditing/EmptyAuditContextProvider.java
?? gems-auditing/src/test/java/br/com/gems/auditing/AuditContextTest.java
?? specs/001-auditoria-keycloak-3.2.0/pacote-de-execucao-SDK-AUD.md
```

## 6. Decisões fora do pacote

O pacote estava não rastreado na árvore, embora a âncora declarasse árvore vazia; foi preservado e não editado. Nenhuma alteração fora da tabela EDIT/CREATE foi feita.

## 7. O que está quebrado agora

Nada — `mvn -B -pl gems-auditing -am test` está verde (34 testes; 0 falhas/erros/skips).

## 8. Sessão anterior

- **Executor:** Codex
- **Identificador anterior:** não aplicável; política de sessão `nova`.
