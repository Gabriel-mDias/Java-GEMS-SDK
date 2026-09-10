package br.com.gems.auditing;

/**
 * Grava toda a trilha num único schema, nomeado por configuração.
 * <p>
 * <strong>Sem padrão embutido</strong>: {@code gems.auditing.schema} é obrigatória, e a aplicação não
 * sobe sem ela. Um padrão aqui faria a SDK escolher, por omissão, entre gravar a trilha junto do dado
 * da organização e gravá-la num schema global — que é exatamente a decisão que ela não deve tomar pelo
 * consumidor. Pior: o consumidor que esquecesse de configurar teria uma trilha funcionando num schema
 * que ele não escolheu, e descobriria isso quando precisasse dela.
 * </p>
 */
public class FixedSchemaAuditTrailDestination implements AuditTrailDestination {

    private final String schema;

    public FixedSchemaAuditTrailDestination(String schema) {
        if (schema == null || schema.isBlank()) {
            throw new IllegalStateException(
                    "gems.auditing.schema não configurada. O destino da trilha não tem padrão: declare a "
                            + "propriedade, ou registre um AuditTrailDestination próprio para rotear por organização.");
        }
        this.schema = schema;
    }

    @Override
    public String schemaFor(String entityName) {
        return schema;
    }
}
