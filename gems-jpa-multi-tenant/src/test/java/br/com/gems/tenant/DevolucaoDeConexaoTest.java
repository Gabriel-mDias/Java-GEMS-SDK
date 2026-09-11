package br.com.gems.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MT-7: a conexão devolvida ao pool volta ao schema que tinha ao sair.
 * <p>
 * O teste usa dublê de {@link Connection} de propósito. O que se prova aqui é uma <em>sequência de
 * chamadas</em> — captura, troca, restauração, fechamento — e um banco real esconderia essa sequência
 * atrás do estado final. Com o dublê, a ordem é a asserção.
 * </p>
 */
class DevolucaoDeConexaoTest {

    private static final String SCHEMA_DO_POOL = "public";

    @Test
    @DisplayName("MT-7: a devolução restaura o schema original, e não um destino fixo")
    void devolucaoRestauraOSchemaOriginal() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn(SCHEMA_DO_POOL);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);

        SchemaMultiTenantConnectionProvider provider =
                new SchemaMultiTenantConnectionProvider(dataSource, new TenantSchemaNaming("tenant_", "administracao"));

        Connection obtida = provider.getConnection("acme");
        provider.releaseConnection("acme", obtida);

        InOrder ordem = inOrder(connection);
        ordem.verify(connection).setSchema("tenant_acme");
        ordem.verify(connection).setSchema(SCHEMA_DO_POOL);
        ordem.verify(connection).close();
    }

    @Test
    @DisplayName("MT-7: a conexão é fechada mesmo se a restauração do schema falhar")
    void conexaoEhFechadaMesmoSeARestauracaoFalhar() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn(SCHEMA_DO_POOL);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);

        SchemaMultiTenantConnectionProvider provider =
                new SchemaMultiTenantConnectionProvider(dataSource, new TenantSchemaNaming("tenant_", "administracao"));
        Connection obtida = provider.getConnection("acme");

        // Conexão que já morreu na rede: restaurar o schema falha. Se o fechamento não acontecesse, o
        // pool perderia a conexão e o vazamento seria de recurso, não de dado.
        SQLException falha = new SQLException("conexão morta");
        doThrow(falha).when(connection).setSchema(SCHEMA_DO_POOL);

        assertThatThrownBy(() -> provider.releaseConnection("acme", obtida)).isSameAs(falha);

        verify(connection).close();
    }

    @Test
    @DisplayName("MT-6: em escopo global, o provedor roteia para o schema global configurado")
    void escopoGlobalRoteiaParaOSchemaGlobal() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn(SCHEMA_DO_POOL);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);

        SchemaMultiTenantConnectionProvider provider =
                new SchemaMultiTenantConnectionProvider(dataSource, new TenantSchemaNaming("tenant_", "administracao"));

        provider.getConnection(JpaTenantContext.GLOBAL_TENANT_IDENTIFIER);

        verify(connection).setSchema("administracao");
    }

    @Test
    @DisplayName("MT-6: escopo global sem schema global configurado recusa, em vez de adivinhar")
    void escopoGlobalSemConfiguracaoRecusa() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn(SCHEMA_DO_POOL);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);

        SchemaMultiTenantConnectionProvider provider =
                new SchemaMultiTenantConnectionProvider(dataSource, new TenantSchemaNaming("tenant_", ""));

        assertThatThrownBy(() -> provider.getConnection(JpaTenantContext.GLOBAL_TENANT_IDENTIFIER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gems.tenant.global-schema");

        verify(connection, never()).setSchema(anyString());
    }

    @Test
    @DisplayName("Não há rota implícita: um identificador qualquer vira schema com prefixo, nunca public")
    void naoHaRotaImplicitaParaPublic() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn(SCHEMA_DO_POOL);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);

        SchemaMultiTenantConnectionProvider provider =
                new SchemaMultiTenantConnectionProvider(dataSource, new TenantSchemaNaming("tenant_", "administracao"));

        provider.getConnection("public");

        // A versão anterior comparava o identificador com a constante de tenant padrão e roteava para
        // o schema public quando batia. Hoje "public" é apenas um alias como outro qualquer.
        verify(connection).setSchema("tenant_public");
    }
}
