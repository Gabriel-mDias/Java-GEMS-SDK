package br.com.gems.tenant.config;

import java.util.Map;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

import br.com.gems.tenant.SchemaMultiTenantConnectionProvider;
import br.com.gems.tenant.TenantIdentifierResolver;


/**
 * Configuração de injeção de dependências do JPA (Hibernate) para habilitar Multi-Tenancy.
 * <p>
 * Injeta o provedor de conexões e o resolvedor de inquilino (tenant) nas propriedades do Hibernate,
 * caso a propriedade {@code gems.tenant.enabled} esteja habilitada ({@code true}).
 * </p>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "gems.tenant.enabled", havingValue = "true")
public class MultiTenantJpaConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernateCustomizer(
            SchemaMultiTenantConnectionProvider connectionProvider,
            TenantIdentifierResolver tenantResolver) {
        return (Map<String, Object> hibernateProperties) -> {
            hibernateProperties.put(org.hibernate.cfg.AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            hibernateProperties.put(org.hibernate.cfg.AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        };
    }
}
