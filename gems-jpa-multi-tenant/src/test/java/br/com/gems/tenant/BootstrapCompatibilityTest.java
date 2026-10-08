package br.com.gems.tenant;

import br.com.gems.tenant.config.MultiTenantJpaConfig;
import br.com.gems.tenant.migration.TenantSchemaMigrator;
import br.com.gems.tenant.migration.ValidationSchemaInitializer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BootstrapCompatibilityTest {
    @AfterEach void clear() { JpaTenantContext.clear(); }

    @Test void missingBootstrapSentinelNeverObtainsBusinessConnection() throws Exception {
        var source = mock(DataSource.class);
        var resolver = new BootstrapTenantIdentifierResolver();
        var provider = new BootstrapSchemaConnectionProvider(source, new TenantSchemaNaming("tenant_", "administracao"), "modelo");

        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(BootstrapTenantIdentifierResolver.MISSING_CONTEXT);
        assertThatThrownBy(() -> provider.getConnection(resolver.resolveCurrentTenantIdentifier())).isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> TenantIdentifierValidator.sanitize(BootstrapTenantIdentifierResolver.MISSING_CONTEXT)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(source);
        assertThat(JpaTenantContext.current()).isEmpty();
    }
    @Test void metadataAndBusinessConnectionsBothRestoreActualOriginalSchema() throws SQLException {
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getSchema()).thenReturn("pool_schema", "modelo");
        var provider = new BootstrapSchemaConnectionProvider(source, new TenantSchemaNaming("tenant_", "administracao"), "modelo");

        provider.getConnection("alpha"); provider.releaseConnection("alpha", connection);

        var sequence = inOrder(connection);
        sequence.verify(connection).getSchema(); sequence.verify(connection).setSchema("modelo");
        sequence.verify(connection).getSchema(); sequence.verify(connection).setSchema("tenant_alpha");
        sequence.verify(connection).setSchema("modelo"); sequence.verify(connection).setSchema("pool_schema"); sequence.verify(connection).close();
    }
    @Test void metadataReleaseFailureAbortsConnectionRatherThanReturnsContaminatedSession() throws SQLException {
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getSchema()).thenReturn("pool_schema");
        var provider = new BootstrapSchemaConnectionProvider(source, new TenantSchemaNaming("tenant_", "administracao"), "modelo");
        provider.getAnyConnection(); doThrow(new SQLException("restore")).when(connection).setSchema("pool_schema");

        assertThatThrownBy(() -> provider.releaseAnyConnection(connection)).isInstanceOf(SQLException.class);
        verify(connection).abort(any()); verify(connection).close();
    }
    @Test void legacyNamingAndDirectConfigurationMethodsRemainSourceCompatible() {
        var config = new MultiTenantJpaConfig();
        assertThat(config.tenantSchemaNaming("tenant_", "administracao").schemaFor("  ACME  ")).isEqualTo("tenant_acme");
        assertThat(config.tenantIdentifierResolver()).isExactlyInstanceOf(TenantIdentifierResolver.class);
        assertThat(config.schemaMultiTenantConnectionProvider(mock(DataSource.class), new TenantSchemaNaming("tenant_", "administracao")))
                .isExactlyInstanceOf(SchemaMultiTenantConnectionProvider.class);
        assertThatThrownBy(() -> config.tenantIdentifierResolver().resolveCurrentTenantIdentifier()).isInstanceOf(TenantContextMissingException.class);
    }
    @Test void strictValidationIsAdditiveAsciiRootLocaleAndHonorsPrefixLength() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(new StrictTenantSchemaNaming("tenant_", "administracao").schemaFor("ISTANBUL")).isEqualTo("tenant_istanbul");
            for (String alias : java.util.List.of("public", "pg_catalog", "tenant_alpha", "modelo", " álias ", "a".repeat(57))) {
                assertThatThrownBy(() -> new StrictTenantSchemaNaming("tenant_", "administracao").schemaFor(alias)).isInstanceOf(IllegalArgumentException.class);
            }
            assertThat(new StrictTenantSchemaNaming("tenant_", "administracao").schemaFor("a".repeat(56))).hasSize(63);
            assertThat(new TenantSchemaNaming("tenant_", "administracao").schemaFor("public")).isEqualTo("tenant_public");
        } finally { Locale.setDefault(previous); }
    }
    @Test void validationMigrationCompletesBeforeInitializerReturns() throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class); var statement = mock(java.sql.Statement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
        var migrator = mock(TenantSchemaMigrator.class);

        new ValidationSchemaInitializer(source, migrator, "modelo").afterPropertiesSet();

        var sequence = inOrder(statement, connection, migrator);
        sequence.verify(statement).execute("CREATE SCHEMA IF NOT EXISTS modelo"); sequence.verify(statement).close();
        sequence.verify(connection).close(); sequence.verify(migrator).migrate("modelo");
    }
    @Test void bootstrapResolverPreservesNestedGlobalAndTenantScopeWithoutFakeContext() {
        var resolver = new BootstrapTenantIdentifierResolver();
        try (var tenant = TenantScope.forTenant("alpha")) {
            assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("alpha");
            try (var global = TenantScope.global()) { assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(JpaTenantContext.GLOBAL_TENANT_IDENTIFIER); }
            assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("alpha");
        }
        assertThat(JpaTenantContext.current()).isEmpty();
    }
}
