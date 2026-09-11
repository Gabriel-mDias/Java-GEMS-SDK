package br.com.gems.tenant;

import br.com.gems.tenant.migration.TenantSchemaMigrator;
import br.com.gems.tenant.migration.TenantSchemaSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Migra, no arranque, os schemas das organizações devolvidas por uma consulta SQL configurada.
 * <p>
 * <strong>Caminho anterior, preservado.</strong> Quem declara {@code gems.tenant.client-query} continua
 * atendido: nenhuma API pública sai nesta rodada. Para código novo, o caminho é
 * {@link TenantSchemaSource} com {@code migration.TenantMigrationCoordinator} — declarar a lista de
 * schemas esperados em Java é mais testável do que embutir uma consulta numa propriedade, e o
 * coordenador ainda avisa sobre schema órfão. Os dois nunca correm juntos: a autoconfiguração só
 * registra este quando não há {@link TenantSchemaSource}.
 * </p>
 * <p>
 * <strong>Duas correções.</strong> O prefixo vem de {@link TenantSchemaNaming} — esta classe usava
 * {@code client_tenant_} enquanto os outros dois colaboradores usavam {@code instituicao_}, e a
 * consequência era migrar num schema e servir tráfego de outro, sem erro em lugar algum. E a falha de
 * migração passa a <strong>interromper o arranque</strong>: antes ela era registrada em
 * {@code log.error} e o boot seguia, deixando a aplicação atendendo sobre um schema meio migrado.
 * </p>
 */
@Slf4j
public class MultiTenantLiquibaseConfig implements InitializingBean {

    private final DataSource dataSource;
    private final TenantSchemaNaming naming;
    private final TenantSchemaMigrator migrator;
    private final String clientQuery;

    public MultiTenantLiquibaseConfig(DataSource dataSource, TenantSchemaNaming naming,
            TenantSchemaMigrator migrator, String clientQuery) {
        this.dataSource = dataSource;
        this.naming = naming;
        this.migrator = migrator;
        this.clientQuery = clientQuery;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> aliases = aliasesDoBanco();
        for (String alias : aliases) {
            migrator.migrate(naming.schemaFor(alias));
        }
        log.info("event=TENANT_MIGRATION_BATCH_COMPLETED tenants={} modulo=gems-jpa-multi-tenant", aliases.size());
    }

    private List<String> aliasesDoBanco() {
        List<String> aliases = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultado = statement.executeQuery(clientQuery)) {

            while (resultado.next()) {
                String alias = resultado.getString(1);
                if (alias != null && !alias.isBlank()) {
                    aliases.add(alias.trim().toLowerCase());
                }
            }
        } catch (Exception excecao) {
            // Falha rápida: sem a lista de organizações, as migrações não seriam aplicadas e o banco
            // ficaria silenciosamente atrás do código. Abortar o boot é o comportamento correto.
            throw new IllegalStateException("Falha ao carregar as organizações por gems.tenant.client-query. "
                    + "As migrações multi-tenant não podem prosseguir.", excecao);
        }

        if (aliases.isEmpty()) {
            log.warn("event=TENANT_MIGRATION_EMPTY organizacao={} causa=gems.tenant.client-query sem resultados "
                    + "modulo=gems-jpa-multi-tenant", TenantLogFields.ORGANIZACAO_AUSENTE);
        }
        return aliases;
    }
}
