package br.com.gems.tenant.service;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Serviço responsável pela gestão física de schemas de Multi-Tenancy.
 * <p>
 * Este serviço possui a capacidade de criar programaticamente schemas no banco de dados 
 * e acionar a execução das migrações do Liquibase restritas àquele schema isolado.
 * Geralmente chamado durante o processo de onboarding de um novo cliente (tenant).
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gems.tenant.enabled", havingValue = "true")
public class TenantSchemaService {

    private final DataSource dataSource;

    @Value("${gems.tenant.schema-prefix:instituicao_}")
    private String schemaPrefix;

    @Value("${gems.tenant.liquibase.changelog}")
    private String tenantChangelogPath;

    /**
     * Cria o schema do banco de dados para o cliente e executa o liquibase.
     * @param acronym A sigla (ou identificador único) do tenant.
     */
    public void createSchemaAndRunLiquibase(String acronym) {
        String schemaName = schemaPrefix + acronym.trim().toLowerCase();
        log.info("Starting schema creation and Liquibase execution for schema: {}", schemaName);
        
        try (Connection connection = dataSource.getConnection()) {
            createSchemaIfNotExists(connection, schemaName);

            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schemaName);
            database.setLiquibaseSchemaName(schemaName);

            try (Liquibase liquibase = new Liquibase(tenantChangelogPath, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }

            log.info("Liquibase migration completed successfully for schema: {}", schemaName);
        } catch (Exception e) {
            log.error("Liquibase execution failed for schema: {}", schemaName, e);
            throw new RuntimeException("Liquibase migration failed for tenant schema: " + schemaName, e);
        }
    }

    private void createSchemaIfNotExists(Connection connection, String schemaName) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            log.info("Schema {} created (or already existed).", schemaName);
        }
    }
}
