package br.com.gems.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Resolvedor de Identificador do Tenant do Hibernate.
 * <p>
 * O Hibernate chama este componente sempre que precisa saber de qual banco de dados/schema
 * ler ou gravar dados no cenário atual de transação.
 * </p>
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return JpaTenantContext.getCurrentTenant();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
