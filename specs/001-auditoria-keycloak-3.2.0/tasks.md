# Tasks — Java GEMS SDK 3.2.0

Cada tarefa registra tier/esforço/risco. Delegados recebem pacote fechado e não commitam.

## SDK-AUD — 10 tarefas

- [x] T001 [orquestrador/medium/alto] Congelar `contracts/auditing-3.2.0.md`; risco de API/binário. Aprovado no portão SDK-P00 em 2026-09-21.
- [x] T002 [especializado/medium/alto] Test-first: ampliar `gems-auditing/src/test/java/br/com/gems/auditing/SuperficiePublicaTest.java` para compatibilidade e novos pontos.
- [x] T003 [especializado/medium/médio] Criar testes de normalização/default do contexto em `gems-auditing/src/test/java/br/com/gems/auditing/AuditContextTest.java`.
- [x] T004 [especializado/medium/alto] Criar `AuditContext.java`, `AuditContextProvider.java` e `EmptyAuditContextProvider.java` públicos com Javadoc.
- [x] T005 [especializado/medium/alto] Test-first: ampliar `TransactionalAuditWriterTest.java` com schema antigo, opt-in, identidade, correlação e rollback.
- [x] T006 [especializado/medium/alto] Alterar `TransactionalAuditWriter.java` para SQL 3.1.0/default e SQL contextual opt-in.
- [x] T007 [especializado/medium/alto] Alterar `HibernateAuditListener.java` para resolver contexto uma vez e passá-lo ao writer.
- [x] T008 [especializado/low/médio] Alterar `AuditingAutoConfiguration.java` com provider default e propriedade desligada.
- [x] T009 [mecânico/medium/alto] Mutar id, correlação, opt-in e transação; registrar vermelho e restaurar.
- [x] T010 [orquestrador/medium/alto] Repetir `mvn -B -pl gems-auditing -am test`, revisar diff e aceitar SDK-AUD.

## SDK-KC — 12 tarefas

- [x] T011 [orquestrador/medium/alto] Congelar `contracts/keycloak-admin-3.2.0.md`; risco de API externa. Aprovado no portão SDK-P00 em 2026-09-21.
- [x] T012 [especializado/medium/alto] Criar testes de compilação/reflexão preservando `KeycloakAdminGateway` 3.1.0.
- [x] T013 [especializado/medium/médio] Criar `KeycloakUserSnapshot.java` com cópias imutáveis e validação.
- [x] T014 [especializado/medium/alto] Criar `KeycloakUserLifecycleGateway.java` sem tipos do cliente Keycloak.
- [x] T015 [especializado/medium/alto] Criar `KeycloakRealmRoleGateway.java` com semântica idempotente/aditiva.
- [x] T016 [especializado/high/alto] Test-first: ampliar `KeycloakAdminRestClientTest.java` para leitura, update, enable/disable e delete.
- [x] T017 [especializado/high/alto] Test-first: provar compensação de senha e falha suprimida.
- [x] T018 [especializado/high/alto] Test-first: provar snapshot/restauração de atributos e grupos, join/leave idempotentes.
- [x] T019 [especializado/high/alto] Implementar lifecycle/compensações em `KeycloakAdminRestClient.java`.
- [x] T020 [especializado/high/alto] Test-first e implementar criação/composição de roles diretas em `KeycloakAdminRestClient.java`.
- [x] T021 [especializado/medium/alto] Criar teste de composição consumindo `AuthorizationCatalog` em `gems-security-authorization/src/test/java/br/com/gems/security/authorization/KeycloakRoleCompositionContractTest.java`.
- [x] T022 [orquestrador/medium/alto] Mutar create/enable/delete/snapshot/grupos/roles, restaurar e repetir gate dos dois módulos.

## SDK-REL — 6 tarefas

- [x] T023 [mecânico/medium/alto] Criar consumer smoke 3.1.0; vermelho para quebra muda estado a `aguardando-decisao-major`.
- [x] T024 [especializado/medium/médio] Atualizar README, `AI-CONSUMER-GUIDE.md`, `llms.txt`, `CLAUDE.md` e criar `RELEASE-NOTES-3.2.0.md`.
- [x] T025 [mecânico/low/médio] Executar `mvn versions:set -DnewVersion=3.2.0 -DgenerateBackupPoms=false`; conferir 17 POMs/BOM.
- [x] T026 [mecânico/high/alto] Executar mutações finais e `mvn -B clean install -DgenerateBackupPoms=false`, contagem >= 186.
- [x] T027 [orquestrador/medium/alto] Revisar diff, abrir PR, exigir CI verde, merge e publish identificados.
- [x] T028 [mecânico/medium/alto] Resolver quatro coordenadas em cache limpo via BOM; registrar SHA/run/registry e liberar C11.

## Ordem

`SDK-P00 → SDK-AUD → SDK-KC → SDK-REL`. AUD e KC têm arquivos disjuntos, mas um escritor por vez é
mantido para simplificar a revisão. Release nunca começa com bloco anterior vermelho.
