package br.com.gems.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Set;

/**
 * Responde "este schema existe?" e cria schema de organização.
 * <p>
 * Promovido de {@code meduc-deploy/persistence/tenant/DatabaseSchemaInspector}, generalizado em dois
 * pontos: o prefixo vem de {@link TenantSchemaNaming} em vez de estar literal no código, e a listagem
 * usa esse prefixo em vez de um {@code like 'tenant\_%'} fixo.
 * </p>
 * <p>
 * <strong>Por que criar schema mora aqui e não no caminho de persistência.</strong> Este é o
 * colaborador que o provisionamento usa. O caminho de requisição só consulta
 * {@link #exists(String)} — e recusa com {@link TenantSchemaNotReadyException} quando o schema falta,
 * em vez de criá-lo (MT-3).
 * </p>
 */
@Slf4j
public class SchemaInspector {

    private final JdbcClient jdbc;
    private final DataSource dataSource;
    private final TenantSchemaNaming naming;

    public SchemaInspector(JdbcClient jdbc, DataSource dataSource, TenantSchemaNaming naming) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.naming = naming;
    }

    /** Se o schema existe no banco. */
    public boolean exists(String schema) {
        return Boolean.TRUE.equals(jdbc
                .sql("select exists(select 1 from information_schema.schemata where schema_name = :schema)")
                .param("schema", schema)
                .query(Boolean.class)
                .single());
    }

    /**
     * Recusa se o schema não estiver pronto. É o ponto de aplicação de MT-3.
     *
     * @throws TenantSchemaNotReadyException se o schema não existir.
     */
    public void requireReady(String schema) {
        if (!exists(schema)) {
            log.error("event=TENANT_SCHEMA_NOT_READY organizacao={} causa=schema ausente modulo=gems-jpa-multi-tenant",
                    schema);
            throw new TenantSchemaNotReadyException(schema);
        }
    }

    /** Todos os schemas que carregam o prefixo deste módulo. */
    public Set<String> tenantSchemas() {
        return Set.copyOf(jdbc
                .sql("select schema_name from information_schema.schemata "
                        + "where schema_name like :padrao escape '\\'")
                .param("padrao", naming.schemaPrefix().replace("_", "\\_") + "%")
                .query(String.class)
                .list());
    }

    /**
     * Cria o schema. O nome é validado contra o prefixo em vigor antes de entrar no DDL.
     * <p>
     * A validação não é zelo redundante: o nome entra por concatenação num {@code create schema}, e não
     * há como parametrizar identificador em DDL. {@link TenantSchemaNaming#schemaFor(String)} já
     * sanitiza o alias; esta checagem cobre quem monta o nome por outro caminho.
     * </p>
     */
    public void create(String schema) {
        String prefixo = naming.schemaPrefix();
        if (!schema.startsWith(prefixo) || !schema.equals(naming.schemaFor(schema.substring(prefixo.length())))) {
            throw new IllegalArgumentException("Nome de schema de tenant inválido: " + schema
                    + " (esperado o prefixo '" + prefixo + "' seguido de um alias válido)");
        }

        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("create schema \"" + schema + "\"");
            log.info("event=TENANT_SCHEMA_CREATED organizacao={} modulo=gems-jpa-multi-tenant", schema);
        } catch (SQLException exception) {
            throw new IllegalStateException("Falha ao criar o schema de tenant " + schema, exception);
        }
    }
}
