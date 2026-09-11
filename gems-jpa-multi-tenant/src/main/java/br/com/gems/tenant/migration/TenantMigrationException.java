package br.com.gems.tenant.migration;

/**
 * Lançada quando a migração do schema de uma organização falha.
 * <p>
 * Ela existe porque a falha de migração <strong>tem</strong> que interromper o que a pediu. As duas
 * implementações anteriores deste módulo faziam o oposto: registravam {@code log.error} e seguiam
 * adiante. O resultado é a pior forma de estado inconsistente — a aplicação sobe e atende, com um
 * schema meio migrado que só se revela quando alguma consulta encontra coluna que não existe.
 * </p>
 */
public class TenantMigrationException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public TenantMigrationException(String schema, Throwable causa) {
        super("Falha na migração do schema de tenant " + schema, causa);
    }
}
