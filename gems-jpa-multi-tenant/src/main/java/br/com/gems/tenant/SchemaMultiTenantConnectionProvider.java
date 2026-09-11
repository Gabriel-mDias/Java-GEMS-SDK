package br.com.gems.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provedor de conexões do Hibernate para multi-tenancy por schema.
 * <p>
 * A cada conexão pedida, troca fisicamente o schema para o da organização em contexto, isolando os
 * dados no nível do banco.
 * </p>
 * <p>
 * <strong>Duas correções de isolamento nesta rodada.</strong>
 * </p>
 * <ol>
 *   <li><strong>Sem rota implícita para {@code public}.</strong> A versão anterior comparava o
 *       identificador com a constante de tenant padrão e, quando batia, roteava para o schema
 *       {@code public}. Como o contexto vazio <em>devolvia</em> essa mesma constante, todo acesso sem
 *       escopo caía ali sem erro. O caminho global agora existe, mas só para quem o declarou
 *       (MT-6).</li>
 *   <li><strong>A conexão volta como saiu</strong> (MT-7). A versão anterior devolvia a conexão ao
 *       schema {@code public} — um destino fixo que só era certo por coincidência. Guardamos o schema
 *       que a conexão tinha ao sair do {@link DataSource} e restauramos esse. Sem isso, conexão
 *       devolvida ao pool com schema de organização serve o próximo tomador que não trocar o schema, e
 *       o vazamento acontece sem que ninguém tenha escrito uma linha errada.</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;
    private final TenantSchemaNaming naming;

    /**
     * O schema original de cada conexão em uso, para restaurá-lo na devolução.
     * <p>
     * A chave é a instância da conexão. O Hibernate garante o par
     * {@code getConnection}/{@code releaseConnection} para a mesma instância, e a entrada é removida na
     * devolução — inclusive quando a restauração falha, para que uma conexão descartada não deixe
     * resíduo no mapa.
     * </p>
     */
    private final Map<Connection, String> schemaOriginal = new ConcurrentHashMap<>();

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    /**
     * Uma conexão já apontada para o schema do contexto atual.
     *
     * @param tenantIdentifier o identificador vindo de {@link TenantIdentifierResolver} — o alias da
     *                         organização, ou {@link JpaTenantContext#GLOBAL_TENANT_IDENTIFIER} em
     *                         escopo global. Nunca é nome de schema.
     */
    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        schemaOriginal.put(connection, connection.getSchema());

        String schema = JpaTenantContext.GLOBAL_TENANT_IDENTIFIER.equals(tenantIdentifier)
                ? naming.globalSchema()
                : naming.schemaFor(tenantIdentifier);

        connection.setSchema(schema);
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        String original = schemaOriginal.remove(connection);
        try {
            if (original != null) {
                connection.setSchema(original);
            }
        } finally {
            releaseAnyConnection(connection);
        }
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
