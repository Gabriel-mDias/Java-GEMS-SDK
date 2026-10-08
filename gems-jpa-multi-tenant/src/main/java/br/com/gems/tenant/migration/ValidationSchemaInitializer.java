package br.com.gems.tenant.migration;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;

/** Prepara explicitamente o schema técnico antes de o Hibernate validar metadata. */
public final class ValidationSchemaInitializer implements InitializingBean {
    private final DataSource dataSource;
    private final TenantSchemaMigrator migrator;
    private final String schema;
    public ValidationSchemaInitializer(DataSource dataSource, TenantSchemaMigrator migrator, String schema) {
        if (schema == null || !schema.matches("[a-z][a-z0-9_]{0,62}") || schema.equals("public") || schema.startsWith("pg_")) {
            throw new IllegalArgumentException("Schema técnico inválido");
        }
        this.dataSource = dataSource; this.migrator = migrator; this.schema = schema;
    }
    @Override public void afterPropertiesSet() {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        } catch (SQLException failure) { throw new IllegalStateException("VALIDATION_SCHEMA_CREATION_FAILED"); }
        migrator.migrate(schema);
    }
}
