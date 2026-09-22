# Release notes - 3.2.0

## 3.2.0 (MINOR)

- Contexto opcional de auditoria com identidade estavel e correlacao, desligado por padrao.
- Migre `ID_ACTOR` e `CD_CORRELATION` antes de habilitar o opt-in.
- Lifecycle, snapshots, grupos e roles Keycloak em interfaces publicas especializadas.
- Nenhuma auto-configuracao ou credencial administrativa Keycloak e fornecida.
- APIs preservadas: `AuditActor`, `AuditActorProvider`, `KeycloakAdminGateway` e
  `KeycloakAdminProperties`; a superficie publica e o comportamento 3.1.0 permanecem preservados.
- Propriedade `gems.auditing.context-columns-enabled=true` habilita as novas colunas somente apos a
  migracao do schema; o padrao `false` protege schemas 3.1.0.

## Historico

Consulte `RELEASE-NOTES-3.1.0.md` para as notas anteriores.
