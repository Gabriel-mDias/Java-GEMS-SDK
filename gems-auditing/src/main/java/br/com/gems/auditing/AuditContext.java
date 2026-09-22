package br.com.gems.auditing;

/**
 * Contexto complementar da operação auditada.
 * <p>
 * O contexto é opcional: ele carrega o identificador do ator quando a aplicação o possui e a
 * correlação que permite relacionar a alteração a uma requisição ou processamento. A SDK não cria
 * nenhum dos dois valores; ausência é representada por {@code null}.
 * </p>
 *
 * @param actorId identificador do ator, ou {@code null} quando ausente.
 * @param correlationId identificador de correlação, ou {@code null} quando ausente.
 */
public record AuditContext(String actorId, String correlationId) {

    public AuditContext {
        actorId = normalizar(actorId);
        correlationId = normalizar(correlationId);
    }

    /** Cria o contexto que declara explicitamente a ausência de informações complementares. */
    public static AuditContext empty() {
        return new AuditContext(null, null);
    }

    private static String normalizar(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
