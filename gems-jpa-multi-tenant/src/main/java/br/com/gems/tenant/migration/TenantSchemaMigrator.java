package br.com.gems.tenant.migration;

import br.com.gems.tenant.TenantLogFields;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;

import javax.sql.DataSource;

/**
 * Aplica o changelog do tenant num schema, e <strong>lança</strong> se falhar.
 * <p>
 * Promovido de {@code meduc-deploy/persistence/tenant/TenantLiquibaseMigrator}, que já era a
 * implementação correta — ao contrário das duas que este módulo tinha, ambas engolindo a exceção num
 * {@code log.error}. A diferença entre lançar e registrar, aqui, é a diferença entre não subir e subir
 * com o banco em estado desconhecido.
 * </p>
 * <p>
 * Recebe o <strong>nome do schema</strong>, não uma entidade de registro de tenant: é o que desacopla
 * o migrador do modelo de dados do consumidor.
 * </p>
 */
@Slf4j
public class TenantSchemaMigrator {

    private final DataSource dataSource;
    private final ResourceLoader resourceLoader;
    private final String changelogPath;

    public TenantSchemaMigrator(DataSource dataSource, ResourceLoader resourceLoader, String changelogPath) {
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
        this.changelogPath = changelogPath;
    }

    /**
     * Migra o schema informado.
     *
     * @throws TenantMigrationException se o Liquibase falhar.
     */
    public void migrate(String schema) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setResourceLoader(resourceLoader);
        liquibase.setChangeLog(changelogPath);
        liquibase.setDefaultSchema(schema);
        liquibase.setLiquibaseSchema(schema);

        try {
            liquibase.afterPropertiesSet();
            log.info("event=TENANT_MIGRATION_COMPLETED organizacao={} modulo=gems-jpa-multi-tenant", schema);
        } catch (LiquibaseException excecao) {
            log.error("event=TENANT_MIGRATION_FAILED organizacao={} causa={} modulo=gems-jpa-multi-tenant",
                    schema == null ? TenantLogFields.ORGANIZACAO_AUSENTE : schema, excecao.getMessage(), excecao);
            throw new TenantMigrationException(schema, excecao);
        }
    }
}
