package br.com.gems.tenant;

/**
 * Lançada quando o schema de uma organização não existe ou não está migrado no momento em que uma
 * operação de persistência o pede.
 * <p>
 * É a recusa contratada em MT-3, e o ponto onde este módulo deliberadamente <strong>não</strong> migra
 * nada. Migração é ato do provisionamento ({@code TenantSchemaService}) ou do arranque
 * ({@code migration.TenantMigrationCoordinator}) — nunca do caminho de requisição. Três razões:
 * </p>
 * <ol>
 *   <li>migrar sob demanda exigiria privilégio de DDL no caminho de requisição;</li>
 *   <li>o primeiro request de cada organização pagaria a latência da migração inteira;</li>
 *   <li>dois requests simultâneos sobre o mesmo schema faltante correriam entre si.</li>
 * </ol>
 * <p>
 * Recusar elimina os três. Um schema faltando em produção é falha de provisionamento, e o lugar de
 * descobrir isso é o alerta — não uma migração improvisada no meio de uma transação de negócio.
 * </p>
 */
public class TenantSchemaNotReadyException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public TenantSchemaNotReadyException(String schema) {
        super("Schema de tenant ausente ou não migrado: " + schema
                + ". O provisionamento é que migra; o caminho de persistência apenas recusa.");
    }
}
