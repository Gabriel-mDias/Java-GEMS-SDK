# Contrato público — gems-auditing 3.2.0

```java
public record AuditContext(String actorId, String correlationId) {
    public static AuditContext empty();
}

@FunctionalInterface
public interface AuditContextProvider {
    AuditContext currentContext();
}
```

- `null` do provider é normalizado para `AuditContext.empty()`; strings vazias viram `null`.
- Bean default existe por `@ConditionalOnMissingBean` e devolve contexto vazio.
- `AuditActor`, `AuditActorProvider` e `AuditTrailDestination` permanecem inalterados.
- `gems.auditing.context-columns-enabled` tem default `false`.
- `false`: SQL e schema esperados pela 3.1.0, sem referência às colunas novas.
- `true`: `AUDIT_OPERATION` deve ter `ID_ACTOR varchar(200)` e
  `CD_CORRELATION varchar(200)`; os valores são gravados na mesma conexão.
- Provider/contexto nunca abre transação, consulta banco nem inventa id/correlação.

## Falhas

- Feature ligada sobre schema sem colunas falha como `AuditWriteException`.
- Falha do provider não é engolida; a operação falha para não registrar contexto falso.
- Rollback remove cabeçalho, mudanças e contexto juntos.
