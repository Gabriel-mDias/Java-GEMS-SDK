package br.com.gems.firebase.auth.config;

import br.com.gems.firebase.auth.AuthorizationResolver;
import br.com.gems.firebase.auth.DelegationPolicy;
import br.com.gems.firebase.auth.FirebaseAdminGateway;
import br.com.gems.firebase.auth.FirebaseAuthProperties;
import br.com.gems.firebase.auth.SecurityEventSink;
import br.com.gems.firebase.auth.TenantAliasValidator;
import br.com.gems.firebase.auth.jdbc.JdbcAuthorizationResolver;
import br.com.gems.firebase.auth.jdbc.JdbcGrantService;
import br.com.gems.firebase.auth.jdbc.JdbcIdentityCommandWorker;
import br.com.gems.firebase.auth.jdbc.JdbcSecurityCatalog;
import br.com.gems.firebase.auth.jdbc.JdbcTenantProvisioner;
import br.com.gems.firebase.auth.jdbc.JdbcTenantService;
import br.com.gems.firebase.auth.jdbc.JdbcTenantAdministratorBootstrap;
import br.com.gems.firebase.auth.jdbc.JdbcUserService;
import br.com.gems.firebase.auth.jdbc.JdbcSecurityEventOutbox;
import br.com.gems.security.authorization.AuthorizationCatalog;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC opt-in separado; migrations explícitas, manager nomeado e nenhuma dependência JPA. */
@AutoConfiguration(after = FirebaseAuthAutoConfiguration.class,
        afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnProperty(prefix = "gems.firebase.auth", name = {"enabled", "jdbc-enabled"}, havingValue = "true")
@ConditionalOnBean({DataSource.class, AuthorizationCatalog.class})
public class FirebaseJdbcAutoConfiguration {
    @Bean @ConditionalOnMissingBean(br.com.gems.firebase.auth.TenantAdministratorBootstrap.class)
    public br.com.gems.firebase.auth.TenantAdministratorBootstrap firebaseTenantAdministratorBootstrap(
            @Qualifier("firebaseSecurityJdbcClient") JdbcClient jdbc, @Qualifier("firebaseSecurityTransactions") TransactionTemplate tx,
            SecurityEventSink events, AuthorizationCatalog catalog, DelegationPolicy delegation, Clock clock, TenantAliasValidator aliases,
            br.com.gems.firebase.auth.SecurityActorProvider actors) {
        return new JdbcTenantAdministratorBootstrap(jdbc, tx, events, catalog, delegation, clock, aliases, actors);
    }
    @Bean @ConditionalOnMissingBean(br.com.gems.firebase.auth.SecurityAdministrationReader.class)
    public br.com.gems.firebase.auth.SecurityAdministrationReader firebaseAdministrationReader(
            @Qualifier("firebaseSecurityJdbcClient") JdbcClient jdbc, @Qualifier("firebaseSecurityTransactions") TransactionTemplate tx,
            SecurityEventSink events, AuthorizationCatalog catalog, Clock clock, br.com.gems.firebase.auth.SecurityActorProvider actors) {
        return new br.com.gems.firebase.auth.jdbc.JdbcSecurityAdministrationReader(jdbc, tx, events, catalog, clock, actors);
    }
    @Bean @ConditionalOnMissingBean
    public br.com.gems.firebase.auth.SecurityActorProvider firebaseSecurityActorProvider() {
        return new br.com.gems.firebase.auth.SpringSecurityActorProvider();
    }
    @Bean @ConditionalOnMissingBean
    public JdbcSecurityEventOutbox firebaseSecurityEventOutbox(@Qualifier("firebaseSecurityJdbcClient") JdbcClient jdbc,
            @Qualifier("firebaseSecurityTransactions") TransactionTemplate tx, Clock clock) {
        return new JdbcSecurityEventOutbox(jdbc, tx, clock);
    }
    /** Mesmo pool, chave transacional distinta: segurança nunca usa o ConnectionHolder empresarial. */
    @Bean(name = "firebaseSecurityDataSource", defaultCandidate = false)
    @ConditionalOnMissingBean(name = "firebaseSecurityDataSource")
    public DataSource firebaseSecurityDataSource(DataSource dataSource) {
        return new DelegatingDataSource(independentTarget(dataSource));
    }
    private DataSource independentTarget(DataSource source) {
        var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<DataSource, Boolean>());
        for (int depth = 0; depth < 32; depth++) {
            if (source == null || !visited.add(source)) {
                throw unsupportedSecuritySource("null target or adapter cycle");
            }
            Class<?> type = source.getClass();
            if (type == org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy.class
                    || type == DelegatingDataSource.class) {
                source = ((DelegatingDataSource) source).getTargetDataSource();
            } else {
                if (source instanceof DelegatingDataSource
                        || source instanceof org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
                        || java.lang.reflect.Proxy.isProxyClass(type)) {
                    throw unsupportedSecuritySource("unsupported adapter " + type.getName());
                }
                // Exact physical providers only: opaque wrappers can hide transaction-aware targets.
                if (type == org.springframework.jdbc.datasource.DriverManagerDataSource.class
                        || type == org.springframework.jdbc.datasource.SimpleDriverDataSource.class
                        || type.getName().equals("com.zaxxer.hikari.HikariDataSource")
                        || type.getName().equals("org.postgresql.ds.PGSimpleDataSource")) {
                    return source;
                }
                throw unsupportedSecuritySource("unsupported physical provider " + type.getName());
            }
        }
        throw unsupportedSecuritySource("adapter depth exceeds 32");
    }
    private IllegalStateException unsupportedSecuritySource(String reason) {
        return new IllegalStateException("firebaseSecurityDataSource requires an independent physical pool: " + reason
                + "; configure an explicit isolated firebaseSecurityDataSource for unsupported adapters");
    }
    /** Manager exclusivo da persistência de segurança; não substitui o manager empresarial. */
    @Bean(name = "firebaseSecurityTransactionManager", defaultCandidate = false)
    @ConditionalOnMissingBean(name = "firebaseSecurityTransactionManager")
    public PlatformTransactionManager firebaseSecurityTransactionManager(@Qualifier("firebaseSecurityDataSource") DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
    @Bean(name = "firebaseSecurityJdbcClient", defaultCandidate = false) @ConditionalOnMissingBean(name = "firebaseSecurityJdbcClient")
    public JdbcClient firebaseSecurityJdbcClient(@Qualifier("firebaseSecurityDataSource") DataSource dataSource) { return JdbcClient.create(dataSource); }
    @Bean(name = "firebaseSecurityTransactions", defaultCandidate = false) @ConditionalOnMissingBean(name = "firebaseSecurityTransactions")
    public TransactionTemplate firebaseSecurityTransactions(@Qualifier("firebaseSecurityTransactionManager") PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
    @Bean @ConditionalOnMissingBean
    public DelegationPolicy firebaseDelegationPolicy() { return Set::of; }
    @Bean(initMethod = "initialize") @ConditionalOnMissingBean
    public JdbcSecurityCatalog firebaseSecurityCatalog(@Qualifier("firebaseSecurityJdbcClient") JdbcClient jdbc,
            @Qualifier("firebaseSecurityTransactions") TransactionTemplate tx, SecurityEventSink events,
            AuthorizationCatalog catalog, DelegationPolicy delegation, Clock clock, FirebaseAuthProperties properties) {
        return new JdbcSecurityCatalog(jdbc, tx, events, catalog, delegation, clock, properties.projectId());
    }
    @Bean @ConditionalOnMissingBean(AuthorizationResolver.class)
    public AuthorizationResolver firebaseJdbcResolver(@Qualifier("firebaseSecurityJdbcClient") JdbcClient jdbc,
            @Qualifier("firebaseSecurityTransactions") TransactionTemplate tx, SecurityEventSink events,
            AuthorizationCatalog catalog, TenantAliasValidator aliases, FirebaseAuthProperties properties, JdbcSecurityCatalog initialized) {
        return new JdbcAuthorizationResolver(jdbc, tx, events, catalog, aliases, properties.projectId(), properties.minimumAppliedMigration());
    }
    @Bean @ConditionalOnMissingBean(br.com.gems.firebase.auth.UserAdministration.class)
    public JdbcUserService firebaseUserService(@Qualifier("firebaseSecurityJdbcClient") JdbcClient jdbc,
            @Qualifier("firebaseSecurityTransactions") TransactionTemplate tx, SecurityEventSink events, Clock clock, FirebaseAuthProperties properties,
            JdbcSecurityCatalog initialized, AuthorizationCatalog catalog, br.com.gems.firebase.auth.SecurityActorProvider actors) {
        return new JdbcUserService(jdbc, tx, events, clock, properties.projectId(), catalog, actors);
    }
    @Bean @ConditionalOnMissingBean(br.com.gems.firebase.auth.TenantAdministration.class)
    public JdbcTenantService firebaseTenantService(@Qualifier("firebaseSecurityJdbcClient") JdbcClient jdbc,
            @Qualifier("firebaseSecurityTransactions") TransactionTemplate tx, SecurityEventSink events, Clock clock, TenantAliasValidator aliases, AuthorizationCatalog catalog,
            br.com.gems.firebase.auth.SecurityActorProvider actors) {
        return new JdbcTenantService(jdbc, tx, events, clock, aliases, catalog, actors);
    }
    @Bean @ConditionalOnMissingBean(br.com.gems.firebase.auth.GrantAdministration.class)
    public JdbcGrantService firebaseGrantService(@Qualifier("firebaseSecurityJdbcClient") JdbcClient jdbc,
            @Qualifier("firebaseSecurityTransactions") TransactionTemplate tx, SecurityEventSink events,
            AuthorizationCatalog catalog, DelegationPolicy delegation, Clock clock, JdbcSecurityCatalog initialized, br.com.gems.firebase.auth.SecurityActorProvider actors) {
        return new JdbcGrantService(jdbc, tx, events, catalog, delegation, clock, actors);
    }
    @Bean @ConditionalOnMissingBean @ConditionalOnBean(FirebaseAdminGateway.class)
    public JdbcIdentityCommandWorker firebaseIdentityCommandWorker(@Qualifier("firebaseSecurityJdbcClient") JdbcClient jdbc,
            @Qualifier("firebaseSecurityTransactions") TransactionTemplate tx, SecurityEventSink events,
            @Qualifier("firebaseSecurityDataSource") DataSource dataSource, FirebaseAdminGateway admin, Clock clock) {
        return new JdbcIdentityCommandWorker(jdbc, tx, events, dataSource, admin, clock, Duration.ofMinutes(2));
    }
    @Bean @ConditionalOnMissingBean
    public JdbcTenantProvisioner firebaseTenantProvisioner(@Qualifier("firebaseSecurityJdbcClient") JdbcClient jdbc,
            @Qualifier("firebaseSecurityTransactions") TransactionTemplate tx, SecurityEventSink events,
            @Qualifier("firebaseSecurityDataSource") DataSource dataSource, TenantAliasValidator aliases, Clock clock) {
        return new JdbcTenantProvisioner(dataSource, jdbc, tx, events, aliases, clock);
    }
}
