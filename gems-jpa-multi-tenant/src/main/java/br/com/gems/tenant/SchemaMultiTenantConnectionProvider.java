package br.com.gems.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provedor de conexões customizado do Hibernate para a estratégia de Multi-Tenancy baseada em Schemas.
 * <p>
 * O Hibernate utiliza esta classe para solicitar uma conexão JDBC. Ao solicitar a conexão,
 * este provedor altera fisicamente o {@code schema} da conexão para o schema referente ao tenant atual,
 * isolando totalmente os dados em nível de banco de dados.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    @Value("${gems.tenant.schema-prefix:instituicao_}")
    private String schemaPrefix;

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    /**
     * Obtém uma conexão com o banco de dados já setada no schema correto do tenant.
     *
     * @param tenantIdentifier O identificador do tenant, fornecido pelo {@link TenantIdentifierResolver}.
     * @return Uma {@link Connection} JDBC pronta para uso no contexto do tenant.
     * @throws SQLException Se não for possível obter a conexão ou alterar o schema.
     */
    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();

        if (JpaTenantContext.DEFAULT_TENANT.equals(tenantIdentifier)) {
            connection.setSchema(JpaTenantContext.DEFAULT_TENANT);
        } else {
            String schemaName = schemaPrefix + TenantIdentifierValidator.sanitize(tenantIdentifier);
            connection.setSchema(schemaName);
        }

        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.setSchema(JpaTenantContext.DEFAULT_TENANT);
        releaseAnyConnection(connection);
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return true;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }
}
