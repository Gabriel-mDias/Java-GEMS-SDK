# Research — decisões 3.2.0

## R-01 — Contexto separado de `AuditActor`

**Decisão**: novo `AuditContextProvider`, não novos componentes no record `AuditActor`.
**Razão**: mudar componentes altera construtor e contrato binário; contexto separado é aditivo.
**Rejeitado**: resolver id pelo login atual, pois rename/delete/reuso corrompem história.

## R-02 — Colunas opt-in

**Decisão**: `gems.auditing.context-columns-enabled=false` por padrão.
**Razão**: inserir colunas novas contra schema 3.1.0 quebra o consumidor mesmo com valores nulos.
**Rejeitado**: detectar coluna por metadata a cada evento; é lento e esconde migration incompleta.

## R-03 — Uma resolução por operação

**Decisão**: listener resolve `AuditContext` uma vez e passa ao writer.
**Razão**: provider de correlação pode depender de contexto mutável; duas leituras divergiriam.

## R-04 — Interfaces especializadas

**Decisão**: manter `KeycloakAdminGateway`; adicionar lifecycle e roles.
**Razão**: método abstrato novo quebra implementores existentes. Default methods mascarariam
capacidade ausente até runtime.

## R-05 — Snapshot próprio

**Decisão**: record da SDK com campos escalares, atributos e ids de grupos.
**Razão**: não vazar `UserRepresentation` congela menos API do Keycloak e permite smoke sem cliente.

## R-06 — Composição aditiva

**Decisão**: comparar filhos diretos, acrescentar faltantes e preservar extras.
**Razão**: lista efetiva inclui herança e não responde o que a composta declara; remover extras é
mudança destrutiva não aprovada.

## R-07 — Compensação de criação

**Decisão**: falha na senha dispara delete; falha do delete fica suprimida na falha principal.
**Razão**: evita conta parcial sem apagar a causa original.

## R-08 — SemVer

**Decisão**: 3.2.0 MINOR somente se smoke 3.1.0 compilar.
**Razão**: API aditiva; qualquer vermelho de compatibilidade para antes de implementação adicional e
exige decisão MAJOR.
