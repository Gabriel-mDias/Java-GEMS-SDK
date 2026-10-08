package br.com.gems.tenant.config;

import br.com.gems.tenant.MultiTenantLiquibaseConfig;
import br.com.gems.tenant.SchemaInspector;
import br.com.gems.tenant.SchemaMultiTenantConnectionProvider;
import br.com.gems.tenant.TenantAliasResolver;
import br.com.gems.tenant.TenantAliasSource;
import br.com.gems.tenant.TenantIdentifierResolver;
import br.com.gems.tenant.TenantSchemaNaming;
import br.com.gems.tenant.StrictTenantSchemaNaming;
import br.com.gems.tenant.BootstrapTenantIdentifierResolver;
import br.com.gems.tenant.BootstrapSchemaConnectionProvider;
import br.com.gems.tenant.migration.ValidationSchemaInitializer;
import br.com.gems.tenant.execution.TenantTaskDecorator;
import br.com.gems.tenant.service.TenantSchemaService;
import br.com.gems.tenant.migration.TenantMigrationCoordinator;
import br.com.gems.tenant.migration.TenantSchemaMigrator;
import br.com.gems.tenant.migration.TenantSchemaSource;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Autoconfiguração do multi-tenancy por schema, ativa com {@code gems.tenant.enabled=true}.
 * <p>
 * <strong>Todos os colaboradores do módulo são registrados aqui, explicitamente.</strong> Antes desta
 * rodada eles eram anotados com {@code @Component} e a autoconfiguração declarava apenas o
 * customizador do Hibernate. Isso só funcionava se o consumidor varresse {@code br.com.gems} — e o
 * pacote raiz de um consumidor é o dele, não o da SDK. Um módulo de biblioteca não pode depender do
 * escopo de varredura de quem o usa: ou os beans existem por autoconfiguração, ou faltam de formas
 * difíceis de diagnosticar.
 * </p>
 * <p>
 * Cada bean é {@link ConditionalOnMissingBean}, o que dá ao consumidor o direito de substituir
 * qualquer peça sem tocar no módulo — a mesma cortesia que {@code gems-model-mapper} já oferece.
 * </p>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "gems.tenant.enabled", havingValue = "true")
public class MultiTenantJpaConfig {

    @Bean("tenantSchemaNaming")
    @ConditionalOnMissingBean
    public TenantSchemaNaming configuredTenantSchemaNaming(
            @Value("${gems.tenant.schema-prefix:" + TenantSchemaNaming.DEFAULT_SCHEMA_PREFIX + "}") String schemaPrefix,
            @Value("${gems.tenant.global-schema:}") String globalSchema,
            @Value("${gems.tenant.strict-alias:false}") boolean strict) {
        return strict ? new StrictTenantSchemaNaming(schemaPrefix, globalSchema) : new TenantSchemaNaming(schemaPrefix, globalSchema);
    }

    @Bean("tenantIdentifierResolver")
    @ConditionalOnMissingBean
    public TenantIdentifierResolver configuredTenantIdentifierResolver(@Value("${gems.tenant.validation-schema:}") String validationSchema) {
        return validationSchema.isBlank() ? new TenantIdentifierResolver() : new BootstrapTenantIdentifierResolver();
    }

    @Bean("schemaMultiTenantConnectionProvider")
    @ConditionalOnMissingBean
    public SchemaMultiTenantConnectionProvider configuredConnectionProvider(
            DataSource dataSource, TenantSchemaNaming naming, @Value("${gems.tenant.validation-schema:}") String validationSchema) {
        return validationSchema.isBlank() ? new SchemaMultiTenantConnectionProvider(dataSource, naming)
                : new BootstrapSchemaConnectionProvider(dataSource, naming, validationSchema);
    }

    @Bean
    @ConditionalOnMissingBean
    public SchemaInspector schemaInspector(JdbcClient jdbc, DataSource dataSource, TenantSchemaNaming naming) {
        return new SchemaInspector(jdbc, dataSource, naming);
    }

    /**
     * O resolvedor de alias recebe as fontes que o consumidor registrou — nenhuma, se ele não
     * registrou. Zero fontes não é erro de configuração: quem instala o contexto por filtro próprio
     * nunca chama este bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantAliasResolver tenantAliasResolver(ObjectProvider<TenantAliasSource> sources) {
        return new TenantAliasResolver(sources.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantTaskDecorator tenantTaskDecorator() {
        return new TenantTaskDecorator();
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantSchemaMigrator tenantSchemaMigrator(DataSource dataSource, ResourceLoader resourceLoader,
            @Value("${gems.tenant.liquibase.changelog}") String changelog) {
        return new TenantSchemaMigrator(dataSource, resourceLoader, changelog);
    }

    /**
     * O coordenador só existe se o consumidor disser quais schemas espera. Sem
     * {@link TenantSchemaSource} não há o que coordenar — e inventar uma lista aqui seria pior do que
     * não migrar.
     */
    @Bean
    @ConditionalOnBean(TenantSchemaSource.class)
    @ConditionalOnProperty(name = "gems.tenant.liquibase.startup-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public TenantMigrationCoordinator tenantMigrationCoordinator(TenantSchemaSource source,
            SchemaInspector schemas, TenantSchemaMigrator migrator) {
        return new TenantMigrationCoordinator(source, schemas, migrator);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantSchemaService tenantSchemaService(TenantSchemaNaming naming, SchemaInspector inspector,
            TenantSchemaMigrator migrator) {
        return new TenantSchemaService(naming, inspector, migrator);
    }

    /**
     * O caminho anterior de migração no arranque, por consulta SQL configurada. Só é registrado quando
     * o consumidor <strong>não</strong> declara {@link TenantSchemaSource} — os dois nunca correm
     * juntos, para que ninguém migre a mesma organização duas vezes por caminhos diferentes.
     */
    @Bean
    @ConditionalOnProperty(name = {"gems.tenant.client-query"})
    @Conditional(StartupMigrationsEnabled.class)
    @ConditionalOnMissingBean({ TenantSchemaSource.class, MultiTenantLiquibaseConfig.class })
    public MultiTenantLiquibaseConfig multiTenantLiquibaseConfig(DataSource dataSource, TenantSchemaNaming naming,
            TenantSchemaMigrator migrator, @Value("${gems.tenant.client-query}") String clientQuery) {
        return new MultiTenantLiquibaseConfig(dataSource, naming, migrator, clientQuery);
    }

    @Bean("hibernateCustomizer")
    public HibernatePropertiesCustomizer configuredHibernateCustomizer(
            SchemaMultiTenantConnectionProvider connectionProvider,
            TenantIdentifierResolver tenantResolver, ObjectProvider<ValidationSchemaInitializer> initializer) {
        initializer.getIfAvailable();
        return (Map<String, Object> hibernateProperties) -> {
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        };
    }

    /** Superfície legada preservada para consumidores que chamam a configuração diretamente. */
    public TenantSchemaNaming tenantSchemaNaming(String prefix, String globalSchema) { return new TenantSchemaNaming(prefix, globalSchema); }
    /** Resolvedor legado sem bootstrap. */
    public TenantIdentifierResolver tenantIdentifierResolver() { return new TenantIdentifierResolver(); }
    /** Provider legado sem schema de metadata. */
    public SchemaMultiTenantConnectionProvider schemaMultiTenantConnectionProvider(DataSource dataSource, TenantSchemaNaming naming) {
        return new SchemaMultiTenantConnectionProvider(dataSource, naming);
    }
    /** Customizer legado, sem migrations técnicas. */
    public HibernatePropertiesCustomizer hibernateCustomizer(SchemaMultiTenantConnectionProvider provider, TenantIdentifierResolver resolver) {
        return properties -> {
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, provider);
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
        };
    }

    @Bean @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "gems.tenant.validation-schema")
    public ValidationSchemaInitializer validationSchemaInitializer(DataSource dataSource, TenantSchemaMigrator migrator,
            @Value("${gems.tenant.validation-schema}") String schema) {
        return new ValidationSchemaInitializer(dataSource, migrator, schema);
    }

    /** Conjunção com client-query sem mudar a interpretação legada daquela propriedade. */
    static final class StartupMigrationsEnabled extends SpringBootCondition {
        @Override public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            boolean enabled = context.getEnvironment().getProperty("gems.tenant.liquibase.startup-enabled", Boolean.class, true);
            return new ConditionOutcome(enabled, "startup migrations opt-in");
        }
    }
}
