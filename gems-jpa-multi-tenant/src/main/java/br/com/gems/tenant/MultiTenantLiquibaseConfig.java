package br.com.gems.tenant;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "gems.tenant.enabled", havingValue = "true")
public class MultiTenantLiquibaseConfig implements InitializingBean {

    private final DataSource dataSource;

    @Value("${gems.tenant.schema-prefix:client_tenant_}")
    private String schemaPrefix;

    @Value("${gems.tenant.client-query}")
    private String getClientsQuery;

    @Value("${gems.tenant.liquibase.changelog}")
    private String tenantChangelogPath;

    public MultiTenantLiquibaseConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Starting Liquibase migrations for all tenants...");
        List<String> tenants = getTenantsFromDatabase();

        for (String tenant : tenants) {
            runLiquibaseForTenant(tenant);
        }

        log.info("Liquibase migrations for all tenants completed.");
    }

    private List<String> getTenantsFromDatabase() {
        List<String> tenants = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(getClientsQuery)) {

            while (rs.next()) {
                String sigla = rs.getString(1);
                if (sigla != null && !sigla.isBlank()) {
                    tenants.add(sigla.trim().toLowerCase());
                }
            }

        } catch (Exception e) {
            // Falha rápida: se não conseguimos listar os tenants, as migrações não serão aplicadas
            // e o estado do banco ficaria silenciosamente inconsistente. Melhor abortar o boot.
            throw new IllegalStateException(
                    "Failed to load tenants from database using gems.tenant.client-query. "
                            + "Multi-tenant Liquibase migrations cannot proceed.", e);
        }

        if (tenants.isEmpty()) {
            log.warn("No tenants returned by gems.tenant.client-query. No tenant migrations will be applied.");
        }
        return tenants;
    }

    private void runLiquibaseForTenant(String tenant) {
        String schemaName = schemaPrefix + tenant;
        log.info("Running Liquibase for schema: {}", schemaName);

        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));

            database.setDefaultSchemaName(schemaName);
            database.setLiquibaseSchemaName(schemaName);

            try (Liquibase liquibase = new Liquibase(tenantChangelogPath, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }

            log.info("Liquibase migration completed for schema: {}", schemaName);
        } catch (Exception e) {
            log.error("Liquibase execution failed for schema: {}", schemaName, e);
        }
    }
}
