package br.com.gems.firebase.auth;

import br.com.gems.firebase.auth.config.FirebaseAuthAutoConfiguration;
import br.com.gems.firebase.auth.config.FirebaseJdbcAutoConfiguration;
import br.com.gems.security.authorization.AuthorizationCatalog;
import java.lang.reflect.Proxy;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirebaseSecurityDataSourceTest {
    record UnsupportedSource(DataSource source, DataSource leaf) { }
    static java.util.stream.Stream<UnsupportedSource> unsupportedSources() {
        var cycle = new DelegatingDataSource();
        cycle.setTargetDataSource(cycle);
        var first = new DelegatingDataSource(); var second = new TransactionAwareDataSourceProxy();
        first.setTargetDataSource(second); second.setTargetDataSource(first);
        var leaf = mock(org.postgresql.ds.PGSimpleDataSource.class);
        DataSource deep = leaf;
        for (int i = 0; i < 33; i++) { deep = new DelegatingDataSource(deep); }
        var lazy = new LazyConnectionDataSourceProxy(leaf);
        lazy.setReadOnlyDataSource(new TransactionAwareDataSourceProxy(leaf));
        var routing = new AbstractRoutingDataSource() {
            @Override protected Object determineCurrentLookupKey() { return "business"; }
        };
        routing.setTargetDataSources(java.util.Map.of("business", leaf));
        DataSource jdkProxy = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
                (proxy, method, args) -> method.invoke(leaf, args));
        return java.util.stream.Stream.of(cycle, first, deep,
                lazy, new LazyConnectionDataSourceProxy(new TransactionAwareDataSourceProxy(leaf)), routing,
                new DelegatingDataSource(new TransactionAwareDataSourceProxy(leaf)) { }, jdkProxy,
                new org.springframework.jdbc.datasource.AbstractDataSource() {
                    @Override public java.sql.Connection getConnection() throws java.sql.SQLException { return new TransactionAwareDataSourceProxy(leaf).getConnection(); }
                    @Override public java.sql.Connection getConnection(String user, String password) throws java.sql.SQLException { return getConnection(); }
                }).map(source -> new UnsupportedSource(source, leaf));
    }
    @ParameterizedTest @MethodSource("unsupportedSources")
    void unsupportedAdaptersFailAtStartupBeforeAcquiringAnyConnection(UnsupportedSource adapter) throws Exception {
        DataSource source = adapter.source();
        // Real SDK auto-configuration rejects the source before catalog startup/security DML.
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FirebaseAuthAutoConfiguration.class, FirebaseJdbcAutoConfiguration.class))
                .withPropertyValues("gems.firebase.auth.enabled=true", "gems.firebase.auth.jdbc-enabled=true", "gems.firebase.auth.project-id=stub-gems")
                .withBean(DataSource.class, () -> source)
                .withBean(AuthorizationCatalog.class, () -> new AuthorizationCatalog(Set.of(), Set.of()))
                .withBean(IdTokenVerifier.class, () -> token -> { throw FirebaseAuthException.denied(); })
                .withBean(FirebaseAdminGateway.class, () -> mock(FirebaseAdminGateway.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("firebaseSecurityDataSource requires an independent physical pool");
                });
        verify(adapter.leaf(), never()).getConnection();
        verify(adapter.leaf(), never()).getConnection(anyString(), anyString());
    }
    @Test void exactTransparentChainKeepsOriginalPoolWithoutAcquiringOrOwningIt() {
        DataSource pool = mock(org.postgresql.ds.PGSimpleDataSource.class);
        var chain = new DelegatingDataSource(new TransactionAwareDataSourceProxy(new TransactionAwareDataSourceProxy(pool)));
        var security = new FirebaseJdbcAutoConfiguration().firebaseSecurityDataSource(chain);
        assertThat(security).isNotSameAs(pool).isNotSameAs(chain);
        assertThat(((DelegatingDataSource) security).getTargetDataSource()).isSameAs(pool);
        verifyNoInteractions(pool);
    }
    @Test void nullSourceFailsExplicitly() {
        var config = new FirebaseJdbcAutoConfiguration();
        for (DataSource source : new DataSource[] {null, new DelegatingDataSource(), new TransactionAwareDataSourceProxy()}) {
            assertThatThrownBy(() -> config.firebaseSecurityDataSource(source))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("null target");
        }
    }
}
