package br.com.gems.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.sql.DataSource;

/** Metadata/ddl validate usam modelo; nenhum identificador empresarial pode rotear para esse schema. */
public final class BootstrapSchemaConnectionProvider extends SchemaMultiTenantConnectionProvider {
    private final String validationSchema;
    private final Map<Connection, String> originals = java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    public BootstrapSchemaConnectionProvider(DataSource dataSource, TenantSchemaNaming naming, String validationSchema) {
        super(dataSource, naming);
        if (validationSchema == null || !validationSchema.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Schema de validação inválido");
        }
        this.validationSchema = validationSchema;
    }
    @Override public Connection getAnyConnection() throws SQLException {
        Connection connection = super.getAnyConnection();
        try {
            originals.put(connection, connection.getSchema());
            connection.setSchema(validationSchema);
            return connection;
        } catch (SQLException failure) {
            originals.remove(connection); connection.close(); throw failure;
        }
    }
    @Override public void releaseAnyConnection(Connection connection) throws SQLException {
        String original = originals.remove(connection);
        try { connection.setSchema(original); }
        catch (SQLException failure) { connection.abort(Runnable::run); throw failure; }
        finally { super.releaseAnyConnection(connection); }
    }
    @Override public Connection getConnection(String identifier) throws SQLException {
        if (BootstrapTenantIdentifierResolver.MISSING_CONTEXT.equals(identifier)) {
            throw new TenantContextMissingException("Contexto empresarial obrigatório antes de obter conexão");
        }
        return super.getConnection(identifier);
    }
}
