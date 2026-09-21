# Plano — Auditoria e administração Keycloak 3.2.0

## Contexto técnico

Java 21, Spring Boot 4.1, Maven multi-módulo. Base com 17 POMs e 186 testes. Módulos afetados:
`gems-auditing`, `gems-keycloak-admin`, `gems-security-authorization` (somente integração de
contrato), `gems-bom` e documentação/release.

## Constitution check

O repositório não possui constituição nem `AGENTS.md`; `CLAUDE.md` é a instrução local disponível.
A entrega respeita: SemVer, Javadoc público, versões centralizadas, zero auto-config de credenciais,
falhas Keycloak sanitizadas, auditoria na transação do chamador e observabilidade sem dependência de
`gems-observability`.

## Desenho

### Auditoria

- Criar `AuditContext(actorId, correlationId)` e `AuditContextProvider` públicos.
- Default `EmptyAuditContextProvider` devolve contexto vazio e nunca inventa identidade.
- Propriedade `gems.auditing.context-columns-enabled=false` mantém o SQL 3.1.0 por padrão.
- Quando ligada, o writer usa `ID_ACTOR` e `CD_CORRELATION`, na mesma conexão.
- `AuditActor` não muda: construtor canônico e accessors 3.1.0 permanecem binariamente estáveis.

### Keycloak

- `KeycloakAdminGateway` permanece byte a byte em assinaturas públicas.
- Criar `KeycloakUserLifecycleGateway` e `KeycloakRealmRoleGateway`.
- `KeycloakAdminRestClient` implementa as três interfaces sem auto-configuração.
- `KeycloakUserSnapshot` é tipo da SDK, sem expor representação do cliente Keycloak.
- Snapshot/restauração inclui atributos e ids de grupos; criação compensa senha.
- Roles: criação idempotente e composição apenas aditiva sobre filhos diretos.

## Checkpoints

| Checkpoint | Escopo | Gate | Estado inicial |
| :-- | :-- | :-- | :-- |
| SDK-P00 | artefatos SDD, contratos, 28 tarefas e analyze | `git diff --check`; portão humano | em curso |
| SDK-AUD | `gems-auditing/**` | `mvn -B -pl gems-auditing -am test` + mutações | bloqueado pelo portão |
| SDK-KC | `gems-keycloak-admin/**` e teste de composição authorization | `mvn -B -pl gems-keycloak-admin,gems-security-authorization -am test` + mutações | bloqueado por SDK-AUD/portão conforme ledger |
| SDK-REL | versão, BOM, docs, reator, PR e publicação | `mvn -B clean install`; smoke/resolução limpa | bloqueado pelos dois blocos |

## Delegação e esforço

- Contratos, arquitetura e aceite: tier orquestrador, esforço medium, risco alto; sem delegação.
- SDK-AUD: executor especializado/medium; 10 tarefas coesas, arquivos fechados e gate de módulo.
- SDK-KC: executor especializado/medium; 12 tarefas repetitivas com transporte/testes localizados.
- Verificações e smoke: executor mecânico/medium, sempre repetidos pelo orquestrador.
- Economia esperada supera pacote/revisão nos dois blocos de implementação; nenhum executor comita.

## Complexidade

O opt-in de colunas é complexidade necessária para compatibilidade real com schemas 3.1.0. Novas
interfaces são necessárias porque acrescentar métodos abstratos ao gateway existente quebraria
implementações consumidoras. Nenhuma nova dependência entre módulos é criada.
