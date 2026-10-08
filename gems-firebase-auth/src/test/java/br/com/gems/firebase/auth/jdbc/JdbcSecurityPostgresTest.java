package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.FirebaseAdminGateway;
import br.com.gems.firebase.auth.FirebaseAuthException;
import br.com.gems.firebase.auth.FirebaseAuthProperties;
import br.com.gems.firebase.auth.TenantAliasValidator;
import br.com.gems.firebase.auth.DelegationPolicy;
import br.com.gems.firebase.auth.AuthorizationResolver;
import br.com.gems.firebase.auth.config.FirebaseJdbcAutoConfiguration;
import br.com.gems.firebase.auth.SecurityEventSink;
import br.com.gems.firebase.auth.StrictTenantAliasValidator;
import br.com.gems.firebase.auth.VerifiedIdentity;
import br.com.gems.security.authorization.AuthorizationCatalog;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Provas de constraints, identidade, cadeia CTE e lifecycle contra PostgreSQL real. */
@Testcontainers
class JdbcSecurityPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final String PROJECT = "demo-gems";
    static final AuthorizationCatalog CATALOG = new AuthorizationCatalog(Set.of("CONSULTAR_CONTA"), Set.of("CONSULTAR_DEMO", "ALTERAR_DEMO"));
    static final br.com.gems.firebase.auth.SecurityActorProvider BOOTSTRAP = br.com.gems.firebase.auth.SecurityActorProvider.trustedBootstrap(new UUID(0, 1));
    PGSimpleDataSource source;
    JdbcClient jdbc;
    TransactionTemplate tx;
    JdbcUserService users;
    JdbcTenantService tenants;
    JdbcGrantService grants;
    JdbcAuthorizationResolver resolver;
    final SecurityEventSink events = event -> { };

    @BeforeEach void prepare() throws Exception {
        source = new PGSimpleDataSource(); source.setURL(POSTGRES.getJdbcUrl()); source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
        jdbc = JdbcClient.create(source); tx = new TransactionTemplate(new JdbcTransactionManager(source));
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS security CASCADE");
            statement.execute("CREATE SCHEMA security");
        }
        // Consumer Liquibase executa o entrypoint público, não somente o SQL interno.
        try (var connection = source.getConnection()) {
            var database = liquibase.database.DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new liquibase.database.jvm.JdbcConnection(connection));
            database.setDefaultSchemaName("security");
            try (var liquibase = new liquibase.Liquibase("db/changelog/gems-firebase-auth/master.xml", new liquibase.resource.ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new liquibase.Contexts(), new liquibase.LabelExpression());
            }
        }
        new JdbcSecurityCatalog(jdbc, tx, events, CATALOG, CATALOG::tenantActions, CLOCK, PROJECT).initialize();
        users = new JdbcUserService(jdbc, tx, events, CLOCK, PROJECT, CATALOG, BOOTSTRAP);
        tenants = new JdbcTenantService(jdbc, tx, events, CLOCK, new StrictTenantAliasValidator(), CATALOG, BOOTSTRAP);
        grants = new JdbcGrantService(jdbc, tx, events, CATALOG, CATALOG::tenantActions, CLOCK, BOOTSTRAP);
        resolver = new JdbcAuthorizationResolver(jdbc, tx, events, CATALOG, new StrictTenantAliasValidator(), PROJECT, 2);
    }
    VerifiedIdentity identity(String uid, String email, String provider, boolean verified) {
        return new VerifiedIdentity(PROJECT, "https://securetoken.google.com/" + PROJECT, uid, email, verified, provider, "Person", NOW);
    }
    UUID bound(String uid) {
        UUID user = users.preProvisionGoogle("Person", uid + "@example.test");
        resolver.resolve(identity(uid, uid + "@example.test", "google.com", true), Optional.empty(), NOW); return user;
    }
    UUID ready(String alias) {
        UUID tenant = tenants.create(alias, alias);
        execute("UPDATE security.tenant SET cd_provisionamento='READY',nr_revisao_aplicada=2 WHERE id_tenant=?", tenant); return tenant;
    }
    void execute(String sql, Object... params) { jdbc.sql(sql).params(params).update(); }
    long count(String table) { return jdbc.sql("SELECT count(*) FROM security." + table).query(Long.class).single(); }
    Set<String> actions(String uid, String alias) {
        return resolver.resolve(identity(uid, uid + "@example.test", "google.com", true), Optional.of(alias), NOW).context().tenantActions();
    }

    @Test void googleLinksOnlyEligibleVerifiedProviderAndNeverReusesEndedUid() {
        UUID user = users.preProvisionGoogle("Person", "person@example.test");
        for (var token : List.of(identity("uid", "person@example.test", "password", true), identity("uid", "person@example.test", "google.com", false))) {
            assertThatThrownBy(() -> resolver.resolve(token, Optional.empty(), NOW)).isInstanceOf(FirebaseAuthException.class);
        }
        assertThat(count("usuario_identidade")).isZero();
        assertThat(resolver.resolve(identity("uid", "person@example.test", "google.com", true), Optional.empty(), NOW).principal().userId()).isEqualTo(user);
        execute("UPDATE security.usuario_identidade SET dt_fim=dt_inicio WHERE id_usuario=?", user);
        users.preProvisionGoogle("Second", "person@example.test");
        assertThatThrownBy(() -> resolver.resolve(identity("uid", "person@example.test", "google.com", true), Optional.empty(), NOW)).isInstanceOf(FirebaseAuthException.class);
        assertThat(count("usuario_identidade")).isEqualTo(1);
    }
    @Test void duplicatePendingCandidatesCannotLink() {
        users.preProvisionGoogle("One", "same@example.test"); users.preProvisionGoogle("Two", "same@example.test");

        assertThatThrownBy(() -> resolver.resolve(identity("uid", "same@example.test", "google.com", true), Optional.empty(), NOW)).isInstanceOf(FirebaseAuthException.class);
        assertThat(count("usuario_identidade")).isZero();
    }
    @Test void concurrentGoogleLinkResolvesSameImmutableOwner() throws Exception {
        UUID user = users.preProvisionGoogle("Person", "race@example.test");
        var token = identity("race", "race@example.test", "google.com", true);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return resolver.resolve(token, Optional.empty(), NOW).principal().userId(); });
            var second = pool.submit(() -> { start.await(); return resolver.resolve(token, Optional.empty(), NOW).principal().userId(); });
            start.countDown();

            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(user);
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(user);
        }
        assertThat(count("usuario_identidade")).isEqualTo(1);
    }
    @Test void cteDeduplicatesEveryInheritanceAndSuspensionCutsChain() {
        UUID user = bound("uid"); UUID tenant = ready("alpha"); UUID membership = tenants.addMember(tenant, user);
        UUID profile = grants.createProfile(tenant, "READER", "Reader"); UUID group = grants.createGroup(tenant, "TEAM", "Team");
        grants.link(JdbcGrantService.Link.GROUP_MEMBER, tenant, group, membership);
        grants.link(JdbcGrantService.Link.GROUP_PROFILE, tenant, group, profile);
        grants.link(JdbcGrantService.Link.MEMBER_PROFILE, tenant, membership, profile);
        grants.grant(JdbcGrantService.Target.PROFILE, tenant, profile, "CONSULTAR_DEMO");
        UUID direct = grants.grant(JdbcGrantService.Target.MEMBER, tenant, membership, "CONSULTAR_DEMO");
        grants.grant(JdbcGrantService.Target.GROUP, tenant, group, "ALTERAR_DEMO");

        assertThat(actions("uid", "alpha")).containsExactlyInAnyOrder("CONSULTAR_DEMO", "ALTERAR_DEMO");
        grants.revoke(JdbcGrantService.Target.MEMBER, tenant, direct);
        grants.suspendProfile(tenant, profile);
        assertThat(actions("uid", "alpha")).containsExactly("ALTERAR_DEMO");
        grants.suspendGroup(tenant, group);
        assertThat(actions("uid", "alpha")).isEmpty();
    }
    @Test void everyCurrentLinkRoleAndProviderMustBeLive() {
        UUID user = bound("uid"); UUID tenant = ready("alpha"); UUID membership = tenants.addMember(tenant, user);
        UUID grant = grants.grant(JdbcGrantService.Target.MEMBER, tenant, membership, "CONSULTAR_DEMO");
        assertThat(actions("uid", "alpha")).containsExactly("CONSULTAR_DEMO");
        execute("UPDATE security.usuario_tenant_role SET dt_fim=dt_inicio WHERE id_usuario_tenant_role=?", grant);
        assertThat(actions("uid", "alpha")).isEmpty();
        execute("UPDATE security.provedor_identidade SET dt_fim=dt_inicio");
        assertThatThrownBy(() -> actions("uid", "alpha")).isInstanceOf(FirebaseAuthException.class);
    }
    @Test void tenantRequiresMembershipReadinessAndRevision() {
        UUID user = bound("uid"); UUID tenant = tenants.create("alpha", "Alpha"); tenants.addMember(tenant, user);
        assertThatThrownBy(() -> actions("uid", "alpha")).isInstanceOf(FirebaseAuthException.class);
        execute("UPDATE security.tenant SET cd_provisionamento='READY',nr_revisao_aplicada=1 WHERE id_tenant=?", tenant);
        assertThatThrownBy(() -> actions("uid", "alpha")).isInstanceOf(FirebaseAuthException.class);
        execute("UPDATE security.tenant SET nr_revisao_aplicada=2 WHERE id_tenant=?", tenant);
        assertThat(actions("uid", "alpha")).isEmpty();
        ready("beta");
        assertThatThrownBy(() -> actions("uid", "beta")).isInstanceOf(FirebaseAuthException.class);
    }
    @Test void crossTenantAndScopeConstraintsRejectDirectSql() {
        UUID user = bound("uid"); UUID alpha = ready("alpha"); UUID beta = ready("beta");
        UUID member = tenants.addMember(alpha, user); UUID profile = grants.createProfile(beta, "P", "Profile"); UUID group = grants.createGroup(beta, "G", "Group");
        assertThatThrownBy(() -> execute("INSERT INTO security.usuario_tenant_perfil(id_usuario_tenant_perfil,id_tenant,id_usuario_tenant,id_perfil,dt_inicio) VALUES(?,?,?,?,?)",
                UUID.randomUUID(), alpha, member, profile, SecurityJdbcSupport.time(NOW))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> execute("INSERT INTO security.grupo_usuario(id_grupo_usuario,id_tenant,id_grupo,id_usuario_tenant,dt_inicio) VALUES(?,?,?,?,?)",
                UUID.randomUUID(), beta, group, member, SecurityJdbcSupport.time(NOW))).isInstanceOf(DataIntegrityViolationException.class);
        UUID global = jdbc.sql("SELECT id_role FROM security.role_seguranca WHERE cd_escopo='GLOBAL'").query(UUID.class).single();
        assertThatThrownBy(() -> execute("INSERT INTO security.usuario_tenant_role(id_usuario_tenant_role,id_tenant,id_usuario_tenant,id_role,dt_inicio) VALUES(?,?,?,?,?)",
                UUID.randomUUID(), alpha, member, global, SecurityJdbcSupport.time(NOW))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("usuario_tenant_perfil")).isZero();
    }
    @Test void intervalCheckAcceptsEmptyAndRejectsNegativeHistoryCannotReopen() {
        UUID user = bound("uid"); UUID tenant = ready("alpha"); UUID member = tenants.addMember(tenant, user);
        assertThatThrownBy(() -> execute("UPDATE security.usuario_tenant SET dt_fim=dt_inicio-interval '1 second' WHERE id_usuario_tenant=?", member))
                .isInstanceOf(DataIntegrityViolationException.class);
        execute("UPDATE security.usuario_tenant SET dt_fim=dt_inicio WHERE id_usuario_tenant=?", member);
        assertThatThrownBy(() -> actions("uid", "alpha")).isInstanceOf(FirebaseAuthException.class);
        assertThatThrownBy(() -> execute("UPDATE security.usuario_tenant SET dt_fim=NULL WHERE id_usuario_tenant=?", member)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(tenants.addMember(tenant, user)).isNotEqualTo(member);
    }
    @Test void identityOwnerProjectAliasScopeAreImmutableAndUidPermanent() {
        UUID user = bound("uid"); UUID another = users.preProvisionGoogle("Other", "other@example.test"); UUID tenant = ready("alpha");
        assertThatThrownBy(() -> execute("UPDATE security.usuario_identidade SET id_usuario=? WHERE id_usuario=?", another, user)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> execute("UPDATE security.tenant SET cd_alias='beta' WHERE id_tenant=?", tenant)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> execute("UPDATE security.provedor_identidade SET cd_projeto='demo-other',cd_emissor='https://securetoken.google.com/demo-other'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> execute("UPDATE security.role_seguranca SET cd_escopo='GLOBAL' WHERE cd_acao='CONSULTAR_DEMO'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        execute("UPDATE security.usuario_identidade SET dt_fim=dt_inicio WHERE id_usuario=?", user);
        assertThatThrownBy(() -> execute("INSERT INTO security.usuario_identidade SELECT ?,?,id_provedor_identidade,cd_emissor,cd_sujeito_externo,cd_email_identidade,dt_inicio,NULL FROM security.usuario_identidade WHERE id_usuario=?",
                UUID.randomUUID(), another, user)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void catalogDriftDoesNotAuthorizeAndScopeChangeFailsStartup() {
        UUID user = bound("uid"); UUID tenant = ready("alpha"); UUID member = tenants.addMember(tenant, user);
        UUID unknown = UUID.randomUUID();
        execute("INSERT INTO security.role_seguranca(id_role,cd_acao,cd_escopo,dt_inicio) VALUES(?,'DESCONHECIDA_ACAO','TENANT',?)", unknown, SecurityJdbcSupport.time(NOW));
        execute("INSERT INTO security.usuario_tenant_role(id_usuario_tenant_role,id_tenant,id_usuario_tenant,id_role,dt_inicio) VALUES(?,?,?,?,?)",
                UUID.randomUUID(), tenant, member, unknown, SecurityJdbcSupport.time(NOW));
        assertThat(actions("uid", "alpha")).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM security.evento_seguranca WHERE cd_evento='AUTHORIZATION_CATALOG_DRIFT'").query(Long.class).single()).isPositive();
        assertThatThrownBy(() -> new JdbcSecurityCatalog(jdbc, tx, events,
                new AuthorizationCatalog(Set.of("CONSULTAR_DEMO"), Set.of("CONSULTAR_CONTA")), Set::of, CLOCK, PROJECT).initialize()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JdbcSecurityCatalog(jdbc, tx, events, CATALOG, Set::of, CLOCK, "demo-other").initialize()).isInstanceOf(IllegalStateException.class);
    }
    @Test void nonDelegableActionAndWrongScopeCannotBeGranted() {
        UUID user = bound("uid"); UUID tenant = ready("alpha"); UUID member = tenants.addMember(tenant, user);
        var restricted = new JdbcGrantService(jdbc, tx, events, CATALOG, Set::of, CLOCK);
        assertThatThrownBy(() -> restricted.grant(JdbcGrantService.Target.MEMBER, tenant, member, "CONSULTAR_DEMO")).isInstanceOf(FirebaseAuthException.class);
        assertThatThrownBy(() -> grants.grant(JdbcGrantService.Target.MEMBER, tenant, member, "CONSULTAR_CONTA")).isInstanceOf(FirebaseAuthException.class);
    }
    JdbcIdentityCommandWorker worker(FirebaseAdminGateway admin) {
        return new JdbcIdentityCommandWorker(jdbc, tx, events, source, admin, CLOCK, Duration.ofMinutes(1));
    }
    @Test void suspensionCommitsFirstAndProviderFailureCannotReopenLocalAccess() {
        UUID user = bound("uid"); UUID tenant = ready("alpha"); UUID member = tenants.addMember(tenant, user);
        grants.grant(JdbcGrantService.Target.MEMBER, tenant, member, "CONSULTAR_DEMO");
        FirebaseAdminGateway admin = mock(FirebaseAdminGateway.class);
        doThrow(new IllegalStateException("secret remote error")).when(admin).setDisabled(anyString(), anyBoolean());
        doThrow(new IllegalStateException("secret remote error")).when(admin).revokeRefreshTokens(anyString());

        users.suspend(user); worker(admin).runOnce();

        assertThatThrownBy(() -> resolver.resolve(identity("uid", "uid@example.test", "google.com", true), Optional.empty(), NOW)).isInstanceOf(FirebaseAuthException.class);
        assertThat(jdbc.sql("SELECT cd_resultado FROM security.comando_identidade WHERE cd_estado='RETRY'").query(String.class).list()).containsExactly("PROVIDER_OPERATION_FAILED");
        assertThat(jdbc.sql("SELECT count(*) FROM security.usuario_tenant_role WHERE dt_fim IS NULL").query(Long.class).single()).isZero();
    }
    @Test void enableRequiresCurrentAckAndNeverReopensGrants() {
        UUID user = bound("uid"); UUID tenant = ready("alpha"); tenants.addMember(tenant, user);
        var admin = mock(FirebaseAdminGateway.class); users.suspend(user);
        var worker = worker(admin); while (worker.runOnce()) { }
        users.requestReactivation(user);
        assertThatThrownBy(() -> resolver.resolve(identity("uid", "uid@example.test", "google.com", true), Optional.empty(), NOW)).isInstanceOf(FirebaseAuthException.class);
        assertThat(worker.runOnce()).isTrue();
        assertThat(resolver.resolve(identity("uid", "uid@example.test", "google.com", true), Optional.empty(), NOW).principal().userId()).isEqualTo(user);
        assertThatThrownBy(() -> actions("uid", "alpha")).isInstanceOf(FirebaseAuthException.class);
        verify(admin).setDisabled("uid", false);
    }
    @Test void newerDisableObsoletesQueuedEnable() {
        UUID user = bound("uid"); var admin = mock(FirebaseAdminGateway.class); users.suspend(user);
        var worker = worker(admin); while (worker.runOnce()) { }
        users.requestReactivation(user); users.suspend(user);
        while (worker.runOnce()) { }

        verify(admin, never()).setDisabled("uid", false);
        assertThat(jdbc.sql("SELECT cd_estado FROM security.comando_identidade WHERE cd_tipo='ENABLE'").query(String.class).single()).isEqualTo("OBSOLETE");
    }
    @Test void expiredLeaseIsRecoverableAndCreateIsUidBound() {
        UUID user = users.provisionPassword("Password", "password@example.test");
        String uid = jdbc.sql("SELECT cd_uid_planejado FROM security.usuario WHERE id_usuario=:id").param("id", user).query(String.class).single();
        execute("UPDATE security.comando_identidade SET cd_estado='CLAIMED',cd_claim=?,dt_lease=?", UUID.randomUUID(), SecurityJdbcSupport.time(NOW.minusSeconds(1)));
        var admin = mock(FirebaseAdminGateway.class);
        when(admin.createUser(uid, "password@example.test", "Password")).thenReturn(new FirebaseAdminGateway.User(uid, "password@example.test", "Password", false, false));

        assertThat(worker(admin).runOnce()).isTrue();
        assertThat(resolver.resolve(identity(uid, "password@example.test", "password", false), Optional.empty(), NOW).principal().userId()).isEqualTo(user);
        assertThat(worker(admin).runOnce()).isFalse();
        verify(admin, times(1)).createUser(uid, "password@example.test", "Password");
    }
    @Test void eventsAreAtomicAppendOnlyAndPublisherFailureReplays() {
        var failing = new JdbcUserService(jdbc, tx, event -> { throw new IllegalStateException("delivery"); }, CLOCK, PROJECT, CATALOG, BOOTSTRAP);
        UUID user = failing.preProvisionGoogle("Person", "event@example.test");
        assertThat(user).isNotNull();
        assertThatThrownBy(() -> execute("DELETE FROM security.evento_seguranca")).isInstanceOf(DataIntegrityViolationException.class);
        var outbox = new JdbcSecurityEventOutbox(jdbc, tx, CLOCK);
        assertThat(outbox.publishNext(event -> { throw new IllegalStateException("offline"); })).isTrue();
        var delivered = new java.util.ArrayList<UUID>();
        var retry = new JdbcSecurityEventOutbox(jdbc, tx, Clock.offset(CLOCK, Duration.ofSeconds(10)));
        while (retry.publishNext(event -> delivered.add(event.id()))) { }
        assertThat(delivered).hasSize((int) count("evento_seguranca"));
        assertThat(jdbc.sql("SELECT count(*) FROM security.entrega_evento WHERE cd_estado='ACKED'").query(Long.class).single()).isEqualTo(count("evento_seguranca"));
    }
    @Test void provisioningProvesRevisionAndKeepsSuspendedReadyInventory() {
        UUID tenant = tenants.create("alpha", "Alpha");
        var provisioner = new JdbcTenantProvisioner(source, jdbc, tx, events, new StrictTenantAliasValidator(), CLOCK);
        var migration = new JdbcTenantProvisioner.Migration() {
            @Override public void apply(Connection connection, String schema) throws Exception {
                try (var statement = connection.createStatement()) { statement.execute("CREATE TABLE IF NOT EXISTS " + schema + ".revision(nr_revision bigint)");
                    statement.execute("DELETE FROM " + schema + ".revision"); statement.execute("INSERT INTO " + schema + ".revision VALUES(3)"); }
            }
            @Override public long appliedRevision(Connection connection, String schema) throws Exception {
                try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT nr_revision FROM " + schema + ".revision")) {
                    result.next(); return result.getLong(1);
                }
            }
        };
        assertThatThrownBy(() -> provisioner.provision(tenant, 4, migration)).isInstanceOf(IllegalStateException.class);
        assertThat(tenants.expectedSchemas()).isEmpty();
        provisioner.provision(tenant, 3, migration); tenants.suspend(tenant);
        assertThat(tenants.expectedSchemas()).containsExactly("tenant_alpha");
        provisioner.provision(tenant, 3, migration);
        assertThat(tenants.expectedSchemas()).containsExactly("tenant_alpha");
    }
    @Test void effectiveQueryPlanUsesExplicitSchemaWithNoSearchPathDependency() throws Exception {
        UUID user = bound("uid"); UUID tenant = ready("alpha"); tenants.addMember(tenant, user);
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("SET search_path=pg_catalog");
            try (var result = statement.executeQuery("EXPLAIN (ANALYZE,BUFFERS) SELECT * FROM security.usuario_identidade WHERE id_usuario='" + user + "'")) {
                assertThat(result.next()).isTrue();
            }
        }
        assertThat(actions("uid", "alpha")).isEmpty();
        UUID provider = jdbc.sql("SELECT id_provedor_identidade FROM security.provedor_identidade").query(UUID.class).single();
        var plan = jdbc.sql("EXPLAIN (ANALYZE,BUFFERS) " + JdbcAuthorizationResolver.EFFECTIVE_SQL)
                .param("user", user).param("provider", provider).param("issuer", "https://securetoken.google.com/" + PROJECT)
                .param("uid", "uid").param("alias", "alpha").param("now", SecurityJdbcSupport.time(NOW)).param("revision", 2)
                .param("tenantCatalog", CATALOG.tenantActions()).param("globalCatalog", CATALOG.globalActions()).query(String.class).list();
        assertThat(plan).anyMatch(line -> line.contains("Planning Time")).anyMatch(line -> line.contains("Execution Time"));
    }
    @Test void minimalSelfGrantsAreAtomicWithUserAndMembership() {
        var selfUsers = new JdbcUserService(jdbc, tx, events, CLOCK, PROJECT, CATALOG, BOOTSTRAP);
        var selfTenants = new JdbcTenantService(jdbc, tx, events, CLOCK, new StrictTenantAliasValidator(), CATALOG, BOOTSTRAP);
        UUID user = selfUsers.preProvisionGoogle("Person", "self@example.test", "CONSULTAR_CONTA");
        var token = identity("self", "self@example.test", "google.com", true);
        assertThat(resolver.resolve(token, Optional.empty(), NOW).context().globalActions()).containsExactly("CONSULTAR_CONTA");
        UUID tenant = ready("alpha"); selfTenants.addMember(tenant, user, "CONSULTAR_DEMO");
        assertThat(resolver.resolve(token, Optional.of("alpha"), NOW).context().tenantActions()).containsExactly("CONSULTAR_DEMO");
        execute("UPDATE security.role_seguranca SET dt_fim=dt_inicio WHERE cd_acao='CONSULTAR_CONTA'");
        long before = count("usuario");
        assertThatThrownBy(() -> selfUsers.preProvisionGoogle("No", "rollback@example.test", "CONSULTAR_CONTA")).isInstanceOf(FirebaseAuthException.class);
        assertThat(count("usuario")).isEqualTo(before);
    }
    @Test void jdbcOptInKeepsNamedManagerSeparateFromConsumerManager() {
        PlatformTransactionManager business = mock(PlatformTransactionManager.class);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FirebaseJdbcAutoConfiguration.class))
                .withPropertyValues("gems.firebase.auth.enabled=true", "gems.firebase.auth.jdbc-enabled=true")
                .withBean(javax.sql.DataSource.class, () -> source).withBean(AuthorizationCatalog.class, () -> CATALOG)
                .withBean(FirebaseAuthProperties.class, () -> new FirebaseAuthProperties(true, PROJECT, true, "X-Tenant-Alias", "test", true, 2,
                        new FirebaseAuthProperties.Emulator(true, "127.0.0.1:9099")))
                .withBean(Clock.class, () -> CLOCK).withBean(TenantAliasValidator.class, StrictTenantAliasValidator::new)
                .withBean(SecurityEventSink.class, () -> events).withBean(DelegationPolicy.class, () -> CATALOG::tenantActions)
                .withBean("transactionManager", PlatformTransactionManager.class, () -> business)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AuthorizationResolver.class);
                    assertThat(context.getBean("transactionManager")).isSameAs(business);
                    assertThat(context.getBean("firebaseSecurityTransactionManager")).isInstanceOf(JdbcTransactionManager.class).isNotSameAs(business);
                });
    }
    @Test void everyAuthorizationTableEnforcesIntervalsAndClosedLinksStayClosed() {
        UUID user = bound("all"); UUID tenant = ready("alpha"); UUID member = tenants.addMember(tenant, user);
        UUID profile = grants.createProfile(tenant, "P", "Profile"); UUID group = grants.createGroup(tenant, "G", "Group");
        grants.link(JdbcGrantService.Link.MEMBER_PROFILE, tenant, member, profile);
        grants.link(JdbcGrantService.Link.GROUP_MEMBER, tenant, group, member);
        grants.link(JdbcGrantService.Link.GROUP_PROFILE, tenant, group, profile);
            for (var target : JdbcGrantService.Target.values()) {
            grants.grant(target, tenant, target == JdbcGrantService.Target.MEMBER ? member : target == JdbcGrantService.Target.PROFILE ? profile : group, "CONSULTAR_DEMO");
        }
        grants.grantGlobal(user, "CONSULTAR_CONTA");
        for (String table : List.of("tenant", "provedor_identidade", "usuario", "usuario_identidade", "usuario_tenant", "role_seguranca",
                "perfil", "perfil_role", "usuario_tenant_perfil", "usuario_tenant_role", "usuario_role_global", "grupo", "grupo_usuario", "grupo_perfil", "grupo_role")) {
            assertThat(count(table)).as(table).isPositive();
            assertThatThrownBy(() -> execute("DELETE FROM security." + table)).as(table).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> execute("UPDATE security." + table + " SET dt_fim=dt_inicio-interval '1 second'"))
                    .as(table).isInstanceOf(DataIntegrityViolationException.class);
            execute("UPDATE security." + table + " SET dt_fim=dt_inicio");
            if (!table.equals("usuario")) {
                assertThatThrownBy(() -> execute("UPDATE security." + table + " SET dt_fim=NULL")).as(table).isInstanceOf(DataIntegrityViolationException.class);
            }
        }
    }
    @Test void allTenantForeignKeysAndEveryRoleScopeAreEnforcedByDirectInsert() {
        UUID user = bound("constraints"); UUID alpha = ready("alpha"); UUID beta = ready("beta");
        UUID ma = tenants.addMember(alpha, user); UUID mb = tenants.addMember(beta, user);
        UUID pa = grants.createProfile(alpha, "P", "Profile"); UUID pb = grants.createProfile(beta, "P", "Profile");
        UUID ga = grants.createGroup(alpha, "G", "Group"); UUID gb = grants.createGroup(beta, "G", "Group");
        UUID role = jdbc.sql("SELECT id_role FROM security.role_seguranca WHERE cd_acao='CONSULTAR_DEMO'").query(UUID.class).single();
        for (Object[] invalid : List.of(
                new Object[]{"perfil_role", "id_perfil", pb, "id_role", role},
                new Object[]{"usuario_tenant_role", "id_usuario_tenant", mb, "id_role", role},
                new Object[]{"grupo_role", "id_grupo", gb, "id_role", role},
                new Object[]{"usuario_tenant_perfil", "id_usuario_tenant", ma, "id_perfil", pb},
                new Object[]{"usuario_tenant_perfil", "id_usuario_tenant", mb, "id_perfil", pa},
                new Object[]{"grupo_usuario", "id_grupo", ga, "id_usuario_tenant", mb},
                new Object[]{"grupo_usuario", "id_grupo", gb, "id_usuario_tenant", ma},
                new Object[]{"grupo_perfil", "id_grupo", ga, "id_perfil", pb},
                new Object[]{"grupo_perfil", "id_grupo", gb, "id_perfil", pa})) {
            String table = (String) invalid[0];
            assertThatThrownBy(() -> execute("INSERT INTO security." + table + "(id_" + table + ",id_tenant," + invalid[1] + "," + invalid[3]
                    + ",dt_inicio) VALUES(?,?,?,?,?)", UUID.randomUUID(), alpha, invalid[2], invalid[4], SecurityJdbcSupport.time(NOW)))
                    .as(table).isInstanceOf(DataIntegrityViolationException.class);
        }
        UUID global = jdbc.sql("SELECT id_role FROM security.role_seguranca WHERE cd_acao='CONSULTAR_CONTA'").query(UUID.class).single();
        for (Object[] target : List.of(new Object[]{"perfil_role", "id_perfil", pa}, new Object[]{"grupo_role", "id_grupo", ga},
                new Object[]{"usuario_tenant_role", "id_usuario_tenant", ma})) {
            assertThatThrownBy(() -> execute("INSERT INTO security." + target[0] + "(id_" + target[0] + ",id_tenant," + target[1]
                    + ",id_role,dt_inicio) VALUES(?,?,?,?,?)", UUID.randomUUID(), alpha, target[2], global, SecurityJdbcSupport.time(NOW)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> execute("INSERT INTO security.usuario_role_global(id_usuario_role_global,id_usuario,id_role,dt_inicio) VALUES(?,?,?,?)",
                UUID.randomUUID(), user, role, SecurityJdbcSupport.time(NOW))).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void activeRemoteEnableSerializesAgainstNewSuspendAndFinalLocalAccessStaysDenied() throws Exception {
        UUID user = bound("serial"); var admin = mock(FirebaseAdminGateway.class); users.suspend(user);
        var worker = worker(admin); while (worker.runOnce()) { }
        users.requestReactivation(user);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); assertThat(release.await(15, TimeUnit.SECONDS)).isTrue(); return null; })
                .when(admin).setDisabled("serial", false);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var enabling = pool.submit(worker::runOnce);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var suspending = pool.submit(() -> users.suspend(user));
            assertThat(jdbc.sql("SELECT dt_fim FROM security.usuario WHERE id_usuario=:user").param("user", user).query(java.sql.Timestamp.class).single()).isNotNull();
            release.countDown(); enabling.get(15, TimeUnit.SECONDS); suspending.get(15, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        while (worker.runOnce()) { }
        assertThatThrownBy(() -> resolver.resolve(identity("serial", "serial@example.test", "google.com", true), Optional.empty(), NOW))
                .isInstanceOf(FirebaseAuthException.class);
        verify(admin, times(2)).setDisabled("serial", true);
    }

    @Test void reactivationBeforeWorkersCannotDiscardRevocationBarrier() {
        UUID user = bound("barrier"); var admin = mock(FirebaseAdminGateway.class);
        users.suspend(user); users.requestReactivation(user);
        var worker = worker(admin);
        while (worker.runOnce()) { }
        var order = inOrder(admin);
        order.verify(admin).revokeRefreshTokens("barrier");
        order.verify(admin).setDisabled("barrier", false);
        assertThat(jdbc.sql("SELECT cd_estado FROM security.comando_identidade WHERE cd_tipo='REVOKE'").query(String.class).single()).isEqualTo("ACKED");
        assertThat(resolver.resolve(identity("barrier", "barrier@example.test", "google.com", true), Optional.empty(), NOW).principal().userId()).isEqualTo(user);
    }
    @Test void permanentlyFailedDisableDoesNotStarveRevocationOrAnotherUserAndRetryIsScheduled() {
        UUID first = bound("first"); UUID second = bound("second"); var admin = mock(FirebaseAdminGateway.class);
        doThrow(new IllegalStateException("remote-secret")).when(admin).setDisabled("first", true);
        users.suspend(first); users.suspend(second);
        execute("UPDATE security.comando_identidade SET dt_criacao=dt_criacao-interval '1 minute' WHERE id_usuario=? AND cd_tipo='DISABLE'", first);
        var worker = worker(admin); int processed = 0;
        while (worker.runOnce()) { assertThat(++processed).isLessThanOrEqualTo(4); }
        verify(admin).revokeRefreshTokens("first"); verify(admin).revokeRefreshTokens("second"); verify(admin).setDisabled("second", true);
        verify(admin, times(1)).setDisabled("first", true);
        assertThat(jdbc.sql("SELECT dt_proxima_tentativa FROM security.comando_identidade WHERE cd_estado='RETRY'").query(java.sql.Timestamp.class).single().toInstant()).isAfter(NOW);
        var restarted = new JdbcIdentityCommandWorker(jdbc, tx, events, source, admin, Clock.offset(CLOCK, Duration.ofSeconds(3)), Duration.ofMinutes(1));
        assertThat(restarted.runOnce()).isTrue(); assertThat(restarted.runOnce()).isFalse();
        verify(admin, times(2)).setDisabled("first", true);
    }
    @Test void createRemoteBeforeAckRecoversAfterCrashAndProvisioningEditsAreExplicitlyDenied() {
        UUID user = users.provisionPassword("Password", "a@example.test");
        String uid = jdbc.sql("SELECT cd_uid_planejado FROM security.usuario WHERE id_usuario=:id").param("id", user).query(String.class).single();
        var admin = mock(FirebaseAdminGateway.class);
        var remote = new java.util.concurrent.atomic.AtomicBoolean();
        when(admin.createUser(uid, "a@example.test", "Password")).thenAnswer(invocation -> {
            if (!remote.getAndSet(true)) {
                // O efeito remoto terminou; força falha SQL no ACK, preservando claim/outbox recuperáveis.
                execute("CREATE FUNCTION security.fail_create_ack() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''ACK_SQL_UNAVAILABLE''; END;'");
                execute("CREATE TRIGGER fail_create_ack BEFORE UPDATE ON security.usuario FOR EACH ROW EXECUTE FUNCTION security.fail_create_ack()");
            }
            return new FirebaseAdminGateway.User(uid, "a@example.test", "Password", false, false);
        });
        assertThat(worker(admin).runOnce()).isTrue();
        assertThat(count("usuario_identidade")).isZero();
        execute("DROP TRIGGER fail_create_ack ON security.usuario");
        assertThatThrownBy(() -> users.update(user, "Renamed", "b@example.test")).hasMessage("ACCOUNT_PROVISIONING_IN_PROGRESS");
        var restarted = new JdbcIdentityCommandWorker(jdbc, tx, events, source, admin, Clock.offset(CLOCK, Duration.ofSeconds(3)), Duration.ofMinutes(1));
        assertThat(restarted.runOnce()).isTrue();
        users.update(user, "Renamed", "b@example.test");
        assertThat(resolver.resolve(identity(uid, "a@example.test", "password", false), Optional.empty(), NOW.plusSeconds(3)).principal().email()).isEqualTo("b@example.test");
        assertThat(count("usuario_identidade")).isEqualTo(1); verify(admin, times(2)).createUser(uid, "a@example.test", "Password");
    }
    @Test void outboxSelectivePermanentFailureAllowsOtherEventsAndRestartUsesBackoff() {
        users.preProvisionGoogle("First", "outbox-one@example.test"); users.preProvisionGoogle("Second", "outbox-two@example.test");
        UUID poison = jdbc.sql("SELECT id_evento FROM security.entrega_evento ORDER BY id_evento LIMIT 1").query(UUID.class).single();
        var attempts = new java.util.ArrayList<UUID>(); var accepted = new java.util.ArrayList<UUID>();
        br.com.gems.firebase.auth.SecurityEventPublisher publisher = event -> {
            attempts.add(event.id()); if (event.id().equals(poison)) { throw new IllegalStateException("publisher-secret"); } accepted.add(event.id());
        };
        var outbox = new JdbcSecurityEventOutbox(jdbc, tx, CLOCK); int processed = 0;
        while (outbox.publishNext(publisher)) { assertThat(++processed).isLessThanOrEqualTo((int) count("evento_seguranca")); }
        assertThat(accepted).hasSize((int) count("evento_seguranca") - 1); assertThat(attempts).containsOnlyOnce(poison);
        assertThat(new JdbcSecurityEventOutbox(jdbc, tx, Clock.offset(CLOCK, Duration.ofSeconds(3))).publishNext(publisher)).isTrue();
        assertThat(attempts.stream().filter(poison::equals).count()).isEqualTo(2);
    }
    @Test void outboxSendBeforeAckCrashAndStaleClaimNeverOverwriteNewAck() {
        users.preProvisionGoogle("First", "ack@example.test");
        UUID id = jdbc.sql("SELECT id_evento FROM security.entrega_evento LIMIT 1").query(UUID.class).single();
        var delivered = new java.util.ArrayList<UUID>();
        var outbox = new JdbcSecurityEventOutbox(jdbc, tx, CLOCK);
        outbox.publishNext(event -> {
            delivered.add(event.id());
            execute("UPDATE security.entrega_evento SET cd_claim=?,dt_lease=?,cd_estado='CLAIMED' WHERE id_evento=?", UUID.randomUUID(), SecurityJdbcSupport.time(NOW.minusSeconds(1)), event.id());
        });
        assertThat(jdbc.sql("SELECT cd_estado FROM security.entrega_evento WHERE id_evento=:id").param("id", id).query(String.class).single()).isEqualTo("CLAIMED");
        new JdbcSecurityEventOutbox(jdbc, tx, CLOCK).publishNext(event -> delivered.add(event.id()));
        assertThat(delivered).containsExactly(id, id);
        outbox.publishNext(event -> { throw new AssertionError("no replay after ACK"); });
        assertThat(jdbc.sql("SELECT cd_estado FROM security.entrega_evento WHERE id_evento=:id").param("id", id).query(String.class).single()).isEqualTo("ACKED");
    }
    @Test void actorSubsetAppliesToAllDirectAndIndirectTargetsAndGlobalServicesStayCentral() {
        UUID actor = bound("actor"); UUID user = bound("recipient"); UUID alpha = ready("alpha"); UUID beta = ready("beta");
        UUID ma = tenants.addMember(alpha, actor); UUID mr = tenants.addMember(alpha, user); tenants.addMember(beta, actor);
        grants.grant(JdbcGrantService.Target.MEMBER, alpha, ma, "CONSULTAR_DEMO");
        UUID profile = grants.createProfile(alpha, "WRITE", "Writer"); UUID group = grants.createGroup(alpha, "WRITERS", "Writers");
        grants.grant(JdbcGrantService.Target.PROFILE, alpha, profile, "ALTERAR_DEMO");
        grants.grant(JdbcGrantService.Target.GROUP, alpha, group, "ALTERAR_DEMO");
        br.com.gems.firebase.auth.SecurityActorProvider trusted = () -> new br.com.gems.firebase.auth.SecurityActorProvider.Actor(actor, false, Optional.of("alpha"));
        var restricted = new JdbcGrantService(jdbc, tx, events, CATALOG, CATALOG::tenantActions, CLOCK, trusted);
        for (var target : JdbcGrantService.Target.values()) {
            UUID owner = target == JdbcGrantService.Target.MEMBER ? mr : target == JdbcGrantService.Target.PROFILE ? profile : group;
            assertThatThrownBy(() -> restricted.grant(target, alpha, owner, "ALTERAR_DEMO")).isInstanceOf(FirebaseAuthException.class);
            assertThat(restricted.grant(target, alpha, owner, "CONSULTAR_DEMO")).isNotNull();
        }
        assertThatThrownBy(() -> restricted.link(JdbcGrantService.Link.MEMBER_PROFILE, alpha, mr, profile)).isInstanceOf(FirebaseAuthException.class);
        assertThatThrownBy(() -> restricted.link(JdbcGrantService.Link.GROUP_MEMBER, alpha, group, mr)).isInstanceOf(FirebaseAuthException.class);
        assertThatThrownBy(() -> restricted.link(JdbcGrantService.Link.GROUP_PROFILE, alpha, group, profile)).isInstanceOf(FirebaseAuthException.class);
        UUID safe = grants.createProfile(alpha, "READ", "Reader"); grants.grant(JdbcGrantService.Target.PROFILE, alpha, safe, "CONSULTAR_DEMO");
        assertThat(restricted.link(JdbcGrantService.Link.MEMBER_PROFILE, alpha, mr, safe)).isNotNull();
        UUID safeGroup = grants.createGroup(alpha, "READERS", "Readers");
        assertThat(restricted.link(JdbcGrantService.Link.GROUP_PROFILE, alpha, safeGroup, safe)).isNotNull();
        assertThat(restricted.link(JdbcGrantService.Link.GROUP_MEMBER, alpha, safeGroup, mr)).isNotNull();
        assertThatThrownBy(() -> restricted.grant(JdbcGrantService.Target.PROFILE, beta, safe, "CONSULTAR_DEMO")).isInstanceOf(FirebaseAuthException.class);
        assertThatThrownBy(() -> restricted.grantGlobal(user, "CONSULTAR_CONTA")).isInstanceOf(FirebaseAuthException.class);
        assertThatThrownBy(() -> new JdbcUserService(jdbc, tx, events, CLOCK, PROJECT, CATALOG, trusted).update(user, "Hijack", "new@example.test")).isInstanceOf(FirebaseAuthException.class);
        assertThatThrownBy(() -> new JdbcUserService(jdbc, tx, events, CLOCK, PROJECT, CATALOG, trusted).suspend(user)).isInstanceOf(FirebaseAuthException.class);
        execute("UPDATE security.usuario_tenant_role SET dt_fim=dt_inicio WHERE id_usuario_tenant=?", ma);
        assertThatThrownBy(() -> restricted.grant(JdbcGrantService.Target.PROFILE, alpha, safe, "CONSULTAR_DEMO")).isInstanceOf(FirebaseAuthException.class);
    }
    @Test void containerMembershipDelegatesInheritedGroupProfileActionsOnlyWhenActorHasThem() {
        UUID actor = bound("actor"); UUID recipient = bound("recipient"); UUID tenant = ready("alpha");
        UUID ma = tenants.addMember(tenant, actor); UUID mr = tenants.addMember(tenant, recipient);
        grants.grant(JdbcGrantService.Target.MEMBER, tenant, ma, "CONSULTAR_DEMO");
        UUID profile = grants.createProfile(tenant, "P", "Profile"); UUID group = grants.createGroup(tenant, "G", "Group");
        grants.grant(JdbcGrantService.Target.PROFILE, tenant, profile, "ALTERAR_DEMO"); grants.link(JdbcGrantService.Link.GROUP_PROFILE, tenant, group, profile);
        var restricted = new JdbcGrantService(jdbc, tx, events, CATALOG, CATALOG::tenantActions, CLOCK, () -> new br.com.gems.firebase.auth.SecurityActorProvider.Actor(actor, false, Optional.of("alpha")));
        assertThatThrownBy(() -> restricted.link(JdbcGrantService.Link.GROUP_MEMBER, tenant, group, mr)).isInstanceOf(FirebaseAuthException.class);
        grants.grant(JdbcGrantService.Target.MEMBER, tenant, ma, "ALTERAR_DEMO");
        assertThat(restricted.link(JdbcGrantService.Link.GROUP_MEMBER, tenant, group, mr)).isNotNull();
        assertThat(actions("recipient", "alpha")).containsExactly("ALTERAR_DEMO");
    }
    @Test void ownPortsAndTypedQueriesProvideMeContextPaginationScopeAndAuditTargets() {
        br.com.gems.firebase.auth.UserAdministration userPort = users;
        br.com.gems.firebase.auth.TenantAdministration tenantPort = tenants;
        br.com.gems.firebase.auth.GrantAdministration grantPort = grants;
        UUID user = bound("consumer"); UUID tenant = ready("alpha"); UUID member = tenantPort.addMember(tenant, user);
        UUID profile = grantPort.createProfile(tenant, "READER", "Reader"); UUID group = grantPort.createGroup(tenant, "TEAM", "Team");
        UUID action = grantPort.grant(br.com.gems.firebase.auth.GrantAdministration.Target.PROFILE, tenant, profile, "CONSULTAR_DEMO");
        grantPort.link(br.com.gems.firebase.auth.GrantAdministration.Link.MEMBER_PROFILE, tenant, member, profile);
        grantPort.link(br.com.gems.firebase.auth.GrantAdministration.Link.GROUP_MEMBER, tenant, group, member);
        var token = identity("consumer", "consumer@example.test", "google.com", true);
        var snapshot = resolver.resolve(token, Optional.of("alpha"), NOW);
        assertThat(snapshot.me().tenants()).extracting("alias", "name").containsExactly(tuple("alpha", "alpha"));
        assertThat(snapshot.tenantContext().orElseThrow().profiles()).extracting("code", "name").containsExactly(tuple("READER", "Reader"));
        assertThat(snapshot.tenantContext().orElseThrow().groups()).extracting("code", "name").containsExactly(tuple("TEAM", "Team"));
        br.com.gems.firebase.auth.SecurityAdministrationReader reader = new JdbcSecurityAdministrationReader(jdbc, tx, events, CATALOG, CLOCK, BOOTSTRAP);
        var query = new br.com.gems.firebase.auth.AdministrationViews.Query(null, 1, true);
        assertThat(reader.findUser(user).orElseThrow().email()).isEqualTo("consumer@example.test");
        assertThat(reader.findTenant(tenant).orElseThrow().alias()).isEqualTo("alpha");
        assertThat(reader.listUsers(query).items()).hasSize(1); assertThat(reader.listTenants(query).items()).hasSize(1);
        assertThat(reader.findProfile(tenant, profile)).isPresent(); assertThat(reader.listProfiles(tenant, query).items()).hasSize(1);
        assertThat(reader.findGroup(tenant, group)).isPresent(); assertThat(reader.listGroups(tenant, query).items()).hasSize(1);
        assertThat(reader.findMembership(tenant, member)).isPresent(); assertThat(reader.listMemberships(tenant, query).items()).hasSize(1);
        assertThat(reader.findGrant(tenant, action).orElseThrow().action()).isEqualTo("CONSULTAR_DEMO");
        var page = reader.listGrants(tenant, query); assertThat(page.nextCursor()).isNotNull();
        assertThat(reader.listGrants(tenant, new br.com.gems.firebase.auth.AdministrationViews.Query(page.nextCursor(), 20, true)).items()).hasSize(2);
        UUID global = grantPort.grantGlobal(user, "CONSULTAR_CONTA");
        assertThat(reader.findGlobalGrant(user, global)).isPresent(); assertThat(reader.listGlobalGrants(user, query).items()).hasSize(1);
        var event = jdbc.sql("SELECT id_ator,cd_tipo_alvo,id_alvo,id_origem,cd_acao FROM security.evento_seguranca WHERE cd_evento='BOOTSTRAP_TENANT_ACTION_GRANTED' AND id_alvo=:id")
                .param("id", action).query().singleRow();
        assertThat(event).containsEntry("id_ator", new UUID(0, 1)).containsEntry("cd_tipo_alvo", "PROFILE_ACTION")
                .containsEntry("id_alvo", action).containsEntry("id_origem", profile).containsEntry("cd_acao", "CONSULTAR_DEMO");
        UUID beta = ready("beta");
        var scoped = new JdbcSecurityAdministrationReader(jdbc, tx, events, CATALOG, CLOCK, () -> new br.com.gems.firebase.auth.SecurityActorProvider.Actor(user, false, Optional.of("alpha")));
        assertThatThrownBy(() -> scoped.listProfiles(beta, query)).isInstanceOf(FirebaseAuthException.class);
        userPort.update(user, "Updated", "updated@example.test");
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> chainEdges() {
        return java.util.stream.Stream.of(
                "DIRECT:usuario", "DIRECT:usuario_identidade", "DIRECT:provedor_identidade", "DIRECT:tenant", "DIRECT:usuario_tenant", "DIRECT:usuario_tenant_role", "DIRECT:role_seguranca",
                "PROFILE:usuario_tenant_perfil", "PROFILE:perfil", "PROFILE:perfil_role", "PROFILE:role_seguranca",
                "GROUP:grupo_usuario", "GROUP:grupo", "GROUP:grupo_role", "GROUP:role_seguranca",
                "GROUP_PROFILE:grupo_usuario", "GROUP_PROFILE:grupo", "GROUP_PROFILE:grupo_perfil", "GROUP_PROFILE:perfil", "GROUP_PROFILE:perfil_role", "GROUP_PROFILE:role_seguranca",
                "GLOBAL:usuario_role_global", "GLOBAL:role_seguranca")
                .flatMap(value -> java.util.stream.Stream.of(false, true).map(future -> {
                    String[] parts = value.split(":"); return org.junit.jupiter.params.provider.Arguments.of(parts[0], parts[1], future);
                }));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("chainEdges")
    void everyIndividualAuthorizationEdgeExpiresOrStartsInFuture(String path, String table, boolean future) {
        UUID user = bound("matrix"); UUID tenant = ready("alpha"); UUID member = tenants.addMember(tenant, user);
        UUID profile = grants.createProfile(tenant, "P", "Profile"); UUID group = grants.createGroup(tenant, "G", "Group");
        switch (path) {
            case "DIRECT" -> grants.grant(JdbcGrantService.Target.MEMBER, tenant, member, "CONSULTAR_DEMO");
            case "PROFILE" -> {
                grants.link(JdbcGrantService.Link.MEMBER_PROFILE, tenant, member, profile);
                grants.grant(JdbcGrantService.Target.PROFILE, tenant, profile, "CONSULTAR_DEMO");
            }
            case "GROUP" -> {
                grants.link(JdbcGrantService.Link.GROUP_MEMBER, tenant, group, member);
                grants.grant(JdbcGrantService.Target.GROUP, tenant, group, "CONSULTAR_DEMO");
            }
            case "GROUP_PROFILE" -> {
                grants.link(JdbcGrantService.Link.GROUP_MEMBER, tenant, group, member);
                grants.link(JdbcGrantService.Link.GROUP_PROFILE, tenant, group, profile);
                grants.grant(JdbcGrantService.Target.PROFILE, tenant, profile, "CONSULTAR_DEMO");
            }
            case "GLOBAL" -> grants.grantGlobal(user, "CONSULTAR_CONTA");
            default -> throw new AssertionError(path);
        }
        if (!path.equals("GLOBAL")) { assertThat(actions("matrix", "alpha")).containsExactly("CONSULTAR_DEMO"); }
        else { assertThat(resolver.resolve(identity("matrix", "matrix@example.test", "google.com", true), Optional.empty(), NOW).context().globalActions()).containsExactly("CONSULTAR_CONTA"); }
        execute("UPDATE security." + table + " SET " + (future ? "dt_inicio=dt_inicio+interval '1 second'" : "dt_fim=dt_inicio"));
        if (Set.of("usuario", "usuario_identidade", "provedor_identidade", "tenant", "usuario_tenant").contains(table)) {
            assertThatThrownBy(() -> actions("matrix", "alpha")).isInstanceOf(FirebaseAuthException.class);
        } else if (path.equals("GLOBAL")) {
            assertThat(resolver.resolve(identity("matrix", "matrix@example.test", "google.com", true), Optional.empty(), NOW).context().globalActions()).isEmpty();
        } else { assertThat(actions("matrix", "alpha")).isEmpty(); }
    }
    @Test void alternatePathKeepsSameActionUntilItsOwnEdgeEnds() {
        UUID user = bound("alternate"); UUID tenant = ready("alpha"); UUID member = tenants.addMember(tenant, user);
        UUID profile = grants.createProfile(tenant, "P", "Profile"); grants.link(JdbcGrantService.Link.MEMBER_PROFILE, tenant, member, profile);
        UUID direct = grants.grant(JdbcGrantService.Target.MEMBER, tenant, member, "CONSULTAR_DEMO");
        UUID inherited = grants.grant(JdbcGrantService.Target.PROFILE, tenant, profile, "CONSULTAR_DEMO");
        grants.revoke(JdbcGrantService.Target.MEMBER, tenant, direct); assertThat(actions("alternate", "alpha")).containsExactly("CONSULTAR_DEMO");
        grants.revoke(JdbcGrantService.Target.PROFILE, tenant, inherited); assertThat(actions("alternate", "alpha")).isEmpty();
    }
    @Test void twoUidsCompetingForOnePendingHaveExactlyOneBinding() throws Exception {
        UUID user = users.preProvisionGoogle("Pending", "pending@example.test");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = List.of("first", "second").stream().map(uid -> pool.submit(() -> {
                start.await(); try { return resolver.resolve(identity(uid, "pending@example.test", "google.com", true), Optional.empty(), NOW).principal().userId(); }
                catch (FirebaseAuthException denied) { return null; }
            })).toList();
            start.countDown(); var owners = new java.util.ArrayList<UUID>();
            for (var job : jobs) { owners.add(job.get(15, TimeUnit.SECONDS)); }
            assertThat(owners).containsExactlyInAnyOrder(user, null);
        }
        assertThat(count("usuario_identidade")).isEqualTo(1);
    }
    @Test void sameUidDifferentPendingCandidatesCannotTransferImmutableOwner() throws Exception {
        UUID first = users.preProvisionGoogle("First", "first@example.test"); UUID second = users.preProvisionGoogle("Second", "second@example.test");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return resolver.resolve(identity("one-uid", "first@example.test", "google.com", true), Optional.empty(), NOW).principal().userId(); });
            var b = pool.submit(() -> { start.await(); return resolver.resolve(identity("one-uid", "second@example.test", "google.com", true), Optional.empty(), NOW).principal().userId(); });
            start.countDown(); UUID owner = a.get(15, TimeUnit.SECONDS); assertThat(b.get(15, TimeUnit.SECONDS)).isEqualTo(owner);
            assertThat(owner).isIn(first, second);
        }
        assertThat(count("usuario_identidade")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM security.usuario WHERE cd_elegibilidade='GOOGLE_PENDING'").query(Long.class).single()).isEqualTo(1);
    }
    @Test void neverBoundClosedChangedCandidatesAndUnknownEmailCannotLink() {
        assertThatThrownBy(() -> resolver.resolve(identity("never", "unknown@example.test", "google.com", true), Optional.empty(), NOW)).isInstanceOf(FirebaseAuthException.class);
        UUID pending = users.preProvisionGoogle("Pending", "old@example.test"); users.update(pending, "Pending", "new@example.test");
        assertThatThrownBy(() -> resolver.resolve(identity("never", "old@example.test", "google.com", true), Optional.empty(), NOW)).isInstanceOf(FirebaseAuthException.class);
        users.suspend(pending);
        assertThatThrownBy(() -> resolver.resolve(identity("never", "new@example.test", "google.com", true), Optional.empty(), NOW)).isInstanceOf(FirebaseAuthException.class);
        assertThat(count("usuario_identidade")).isZero();
    }
    @Test void candidateClosureCommittedWhileLinkWaitsIsRechecked() throws Exception {
        UUID user = users.preProvisionGoogle("Pending", "closed@example.test");
        try (var connection = source.getConnection(); var pool = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("UPDATE security.usuario SET dt_fim=dt_inicio WHERE id_usuario=?")) { statement.setObject(1, user); statement.executeUpdate(); }
            var started = new CountDownLatch(1);
            var job = pool.submit(() -> { started.countDown(); return resolver.resolve(identity("closed", "closed@example.test", "google.com", true), Optional.empty(), NOW); });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%GOOGLE_PENDING%'").query(Long.class).single()).isPositive());
            connection.commit();
            assertThatThrownBy(() -> job.get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(FirebaseAuthException.class);
        }
        assertThat(count("usuario_identidade")).isZero();
    }
    @Test void concurrentDuplicateMembershipAndGrantRetriesPreserveClosedHistory() throws Exception {
        UUID user = bound("duplicate"); UUID tenant = ready("alpha");
        var start = new CountDownLatch(1); UUID membership;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return tenants.addMember(tenant, user); });
            var second = pool.submit(() -> { start.await(); return tenants.addMember(tenant, user); });
            start.countDown(); membership = first.get(15, TimeUnit.SECONDS); assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(membership);
        }
        UUID profile = grants.createProfile(tenant, "P", "Profile"); UUID group = grants.createGroup(tenant, "G", "Group");
        for (var target : JdbcGrantService.Target.values()) {
            UUID owner = target == JdbcGrantService.Target.MEMBER ? membership : target == JdbcGrantService.Target.PROFILE ? profile : group;
            var gate = new CountDownLatch(1); UUID granted;
            try (var pool = Executors.newFixedThreadPool(2)) {
                var a = pool.submit(() -> { gate.await(); return grants.grant(target, tenant, owner, "CONSULTAR_DEMO"); });
                var b = pool.submit(() -> { gate.await(); return grants.grant(target, tenant, owner, "CONSULTAR_DEMO"); });
                gate.countDown(); granted = a.get(15, TimeUnit.SECONDS); assertThat(b.get(15, TimeUnit.SECONDS)).isEqualTo(granted);
            }
            grants.revoke(target, tenant, granted);
            UUID newGrant = grants.grant(target, tenant, owner, "CONSULTAR_DEMO"); assertThat(newGrant).isNotEqualTo(granted);
            assertThat(count(target.table)).isEqualTo(2);
        }
        for (var link : JdbcGrantService.Link.values()) {
            UUID left = link == JdbcGrantService.Link.MEMBER_PROFILE ? membership : group;
            UUID right = link == JdbcGrantService.Link.GROUP_MEMBER ? membership : profile;
            var gate = new CountDownLatch(1); UUID linked;
            try (var pool = Executors.newFixedThreadPool(2)) {
                var a = pool.submit(() -> { gate.await(); return grants.link(link, tenant, left, right); });
                var b = pool.submit(() -> { gate.await(); return grants.link(link, tenant, left, right); });
                gate.countDown(); linked = a.get(15, TimeUnit.SECONDS); assertThat(b.get(15, TimeUnit.SECONDS)).isEqualTo(linked);
            }
            grants.revoke(link, tenant, linked);
            assertThat(grants.link(link, tenant, left, right)).isNotEqualTo(linked); assertThat(count(link.table)).isEqualTo(2);
        }
        tenants.endMembership(tenant, membership); UUID renewed = tenants.addMember(tenant, user);
        assertThat(renewed).isNotEqualTo(membership); assertThat(count("usuario_tenant")).isEqualTo(2);
    }
    @Test void expiredIdentityLeaseAndConcurrentWorkersDoNotAllowStaleAck() throws Exception {
        UUID user = users.provisionPassword("Password", "lease@example.test");
        String uid = jdbc.sql("SELECT cd_uid_planejado FROM security.usuario WHERE id_usuario=:id").param("id", user).query(String.class).single();
        var admin = mock(FirebaseAdminGateway.class); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(admin.createUser(uid, "lease@example.test", "Password")).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) { entered.countDown(); assertThat(release.await(15, TimeUnit.SECONDS)).isTrue(); }
            return new FirebaseAdminGateway.User(uid, "lease@example.test", "Password", false, false);
        });
        Clock later = Clock.offset(CLOCK, Duration.ofMinutes(2));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var stale = pool.submit(worker(admin)::runOnce); assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var current = pool.submit(new JdbcIdentityCommandWorker(jdbc, tx, events, source, admin, later, Duration.ofMinutes(1))::runOnce);
            // A segunda chamada reclama a lease vencida mesmo enquanto espera o lock de sessão.
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(jdbc.sql("SELECT nr_tentativas FROM security.comando_identidade").query(Integer.class).single()).isEqualTo(2));
            release.countDown(); assertThat(stale.get(15, TimeUnit.SECONDS)).isTrue(); assertThat(current.get(15, TimeUnit.SECONDS)).isTrue();
        } finally { release.countDown(); }
        assertThat(count("usuario_identidade")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT cd_estado FROM security.comando_identidade").query(String.class).single()).isEqualTo("ACKED");
    }
    @Test void concurrentTenantProvisioningIsSerializedReadyOnlyAfterVerifiedRevision() throws Exception {
        UUID user = bound("provision"); UUID tenant = tenants.create("race", "Race"); tenants.addMember(tenant, user);
        var entered1 = new CountDownLatch(1); var entered2 = new CountDownLatch(1);
        var release1 = new CountDownLatch(1); var release2 = new CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var migration = new JdbcTenantProvisioner.Migration() {
            @Override public void apply(Connection connection, String schema) throws Exception {
                int round = calls.incrementAndGet();
                (round == 1 ? entered1 : entered2).countDown();
                assertThat((round == 1 ? release1 : release2).await(15, TimeUnit.SECONDS)).isTrue();
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS " + schema + ".hardening_revision(nr bigint)");
                    statement.execute("DELETE FROM " + schema + ".hardening_revision");
                    statement.execute("INSERT INTO " + schema + ".hardening_revision VALUES(" + (round + 2) + ")");
                }
            }
            @Override public long appliedRevision(Connection connection, String schema) throws Exception {
                try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT nr FROM " + schema + ".hardening_revision")) {
                    result.next(); return result.getLong(1);
                }
            }
        };
        var provisioner = new JdbcTenantProvisioner(source, jdbc, tx, events, new StrictTenantAliasValidator(), CLOCK);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> provisioner.provision(tenant, 3, migration)); assertThat(entered1.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> provisioner.provision(tenant, 4, migration));
            assertThatThrownBy(() -> actions("provision", "race")).isInstanceOf(FirebaseAuthException.class);
            assertThat(tenants.expectedSchemas()).isEmpty(); assertThat(calls.get()).isEqualTo(1);
            release1.countDown(); first.get(15, TimeUnit.SECONDS); assertThat(entered2.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> actions("provision", "race")).isInstanceOf(FirebaseAuthException.class);
            release2.countDown(); second.get(15, TimeUnit.SECONDS);
        } finally { release1.countDown(); release2.countDown(); }
        assertThat(actions("provision", "race")).isEmpty();
        assertThat(jdbc.sql("SELECT nr_revisao_aplicada FROM security.tenant WHERE id_tenant=:id").param("id", tenant).query(Long.class).single()).isEqualTo(4);
        assertThat(tenants.expectedSchemas()).containsExactly("tenant_race");
    }
    @Test void failedProvisioningUnlockAbortsPinnedConnectionAndDoesNotLeakAdvisoryLock() throws Exception {
        UUID tenant = tenants.create("unlock", "Unlock"); Connection real = source.getConnection();
        var aborted = new java.util.concurrent.atomic.AtomicBoolean();
        Connection proxy = (Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (object, method, args) -> {
            if (method.getName().equals("prepareStatement") && args[0] instanceof String sql && sql.contains("pg_advisory_unlock")) { throw new java.sql.SQLException("unlock failed"); }
            if (method.getName().equals("abort")) { aborted.set(true); }
            try { return method.invoke(real, args); } catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
        });
        var pool = mock(javax.sql.DataSource.class); when(pool.getConnection()).thenReturn(proxy);
        var migration = new JdbcTenantProvisioner.Migration() {
            @Override public void apply(Connection connection, String schema) { }
            @Override public long appliedRevision(Connection connection, String schema) { return 1; }
        };
        assertThatThrownBy(() -> new JdbcTenantProvisioner(pool, jdbc, tx, events, new StrictTenantAliasValidator(), CLOCK).provision(tenant, 1, migration))
                .hasMessage("TENANT_PROVISIONING_SESSION_FAILED");
        assertThat(aborted).isTrue(); assertThat(real.isClosed()).isTrue();
        new JdbcTenantProvisioner(source, jdbc, tx, events, new StrictTenantAliasValidator(), CLOCK).provision(tenant, 1, migration);
        assertThat(tenants.expectedSchemas()).containsExactly("tenant_unlock");
    }
    @Test void concurrentOutboxPublishersUseLeaseCasAndConsumerDeduplicatesAfterSendBeforeAck() throws Exception {
        users.preProvisionGoogle("Outbox", "concurrent-outbox@example.test");
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var deliveries = java.util.concurrent.ConcurrentHashMap.<UUID>newKeySet();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var old = pool.submit(() -> new JdbcSecurityEventOutbox(jdbc, tx, CLOCK).publishNext(event -> {
                deliveries.add(event.id()); entered.countDown();
                try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); } catch (InterruptedException failure) { throw new AssertionError(failure); }
                throw new IllegalStateException("lost ack");
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var latest = pool.submit(() -> new JdbcSecurityEventOutbox(jdbc, tx, Clock.offset(CLOCK, Duration.ofMinutes(3))).publishNext(event -> deliveries.add(event.id())));
            assertThat(latest.get(10, TimeUnit.SECONDS)).isTrue(); release.countDown(); assertThat(old.get(10, TimeUnit.SECONDS)).isTrue();
        } finally { release.countDown(); }
        assertThat(deliveries).hasSize(1);
        assertThat(jdbc.sql("SELECT cd_estado FROM security.entrega_evento").query(String.class).single()).isEqualTo("ACKED");
    }
    @Test void seedBootstrapIsSeparateExplicitAuditedIdempotentAndNeverAcceptsPendingIdentity() {
        UUID user = bound("seed"); UUID tenant = ready("alpha");
        var seed = new JdbcTenantAdministratorBootstrap(jdbc, tx, events, CATALOG, CATALOG::tenantActions, CLOCK, new StrictTenantAliasValidator(), BOOTSTRAP);
        UUID membership = seed.initialize(tenant, user, Set.of("CONSULTAR_DEMO"));
        assertThat(seed.initialize(tenant, user, Set.of("CONSULTAR_DEMO"))).isEqualTo(membership);
        assertThat(count("usuario_tenant")).isEqualTo(1); assertThat(count("usuario_tenant_role")).isEqualTo(1);
        assertThat(actions("seed", "alpha")).containsExactly("CONSULTAR_DEMO");
        UUID pending = users.preProvisionGoogle("Pending", "pending-seed@example.test");
        assertThatThrownBy(() -> seed.initialize(tenant, pending, Set.of("CONSULTAR_DEMO"))).isInstanceOf(FirebaseAuthException.class);
        var regular = new JdbcTenantAdministratorBootstrap(jdbc, tx, events, CATALOG, CATALOG::tenantActions, CLOCK, new StrictTenantAliasValidator(),
                () -> new br.com.gems.firebase.auth.SecurityActorProvider.Actor(user, false, Optional.of("alpha")));
        assertThatThrownBy(() -> regular.initialize(tenant, user, Set.of("ALTERAR_DEMO"))).isInstanceOf(FirebaseAuthException.class);
        assertThat(jdbc.sql("SELECT id_ator FROM security.evento_seguranca WHERE cd_evento='BOOTSTRAP_TENANT_ADMIN_BOOTSTRAPPED'").query(UUID.class).list()).containsOnly(new UUID(0, 1));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"PROFILE", "GROUP", "GROUP_PROFILE"})
    void actorEffectiveSubsetUsesEveryInheritedPathAndRejectsStaleAuthentication(String path) {
        UUID actor = bound("inherited-actor"); UUID recipient = bound("recipient"); UUID tenant = ready("alpha");
        UUID ma = tenants.addMember(tenant, actor); UUID mr = tenants.addMember(tenant, recipient);
        UUID profile = grants.createProfile(tenant, "P", "Profile"); UUID group = grants.createGroup(tenant, "G", "Group");
        if (path.equals("PROFILE")) { grants.link(JdbcGrantService.Link.MEMBER_PROFILE, tenant, ma, profile); grants.grant(JdbcGrantService.Target.PROFILE, tenant, profile, "CONSULTAR_DEMO"); }
        else {
            grants.link(JdbcGrantService.Link.GROUP_MEMBER, tenant, group, ma);
            if (path.equals("GROUP")) { grants.grant(JdbcGrantService.Target.GROUP, tenant, group, "CONSULTAR_DEMO"); }
            else { grants.link(JdbcGrantService.Link.GROUP_PROFILE, tenant, group, profile); grants.grant(JdbcGrantService.Target.PROFILE, tenant, profile, "CONSULTAR_DEMO"); }
        }
        var snapshot = resolver.resolve(identity("inherited-actor", "inherited-actor@example.test", "google.com", true), Optional.of("alpha"), NOW);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new br.com.gems.firebase.auth.FirebaseAuthenticationToken(snapshot));
        try {
            var regular = new JdbcGrantService(jdbc, tx, events, CATALOG, CATALOG::tenantActions, CLOCK);
            assertThat(regular.grant(JdbcGrantService.Target.MEMBER, tenant, mr, "CONSULTAR_DEMO")).isNotNull();
            assertThatThrownBy(() -> regular.grant(JdbcGrantService.Target.MEMBER, tenant, mr, "ALTERAR_DEMO")).isInstanceOf(FirebaseAuthException.class);
            if (path.equals("GROUP")) { grants.suspendGroup(tenant, group); } else { grants.suspendProfile(tenant, profile); }
            assertThatThrownBy(() -> regular.grant(JdbcGrantService.Target.MEMBER, tenant, mr, "CONSULTAR_DEMO")).isInstanceOf(FirebaseAuthException.class);
            assertThat(snapshot.context().tenantActions()).containsExactly("CONSULTAR_DEMO");
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    @Test void centralConcreteBootstrapActionWorksWithoutTenantMembershipAndIsAuditedToActualActor() {
        UUID central = bound("central"); UUID recipient = bound("seed-recipient"); UUID tenant = ready("alpha");
        var catalog = new AuthorizationCatalog(Set.of("CONSULTAR_CONTA", "CRIAR_ADMINISTRADOR_TENANT"), CATALOG.tenantActions());
        new JdbcSecurityCatalog(jdbc, tx, events, catalog, catalog::tenantActions, CLOCK, PROJECT).initialize();
        new JdbcGrantService(jdbc, tx, events, catalog, catalog::tenantActions, CLOCK, BOOTSTRAP).grantGlobal(central, "CRIAR_ADMINISTRADOR_TENANT");
        var service = new JdbcTenantAdministratorBootstrap(jdbc, tx, events, catalog, catalog::tenantActions, CLOCK, new StrictTenantAliasValidator(),
                () -> new br.com.gems.firebase.auth.SecurityActorProvider.Actor(central, false));
        assertThat(service.initialize(tenant, recipient, Set.of("CONSULTAR_DEMO"))).isNotNull();
        assertThat(actions("seed-recipient", "alpha")).containsExactly("CONSULTAR_DEMO");
        assertThat(jdbc.sql("SELECT id_ator FROM security.evento_seguranca WHERE cd_evento='TENANT_ADMIN_BOOTSTRAPPED'").query(UUID.class).single()).isEqualTo(central);
        assertThat(jdbc.sql("SELECT count(*) FROM security.usuario_tenant WHERE id_usuario=:user").param("user", central).query(Long.class).single()).isZero();
    }
    @Test void inheritedDelegationRequiresPolicyAndCatalogEvenIfActorHasAction() {
        UUID actor = bound("policy-actor"); UUID recipient = bound("policy-recipient"); UUID tenant = ready("alpha");
        UUID ma = tenants.addMember(tenant, actor); UUID mr = tenants.addMember(tenant, recipient);
        grants.grant(JdbcGrantService.Target.MEMBER, tenant, ma, "ALTERAR_DEMO");
        UUID profile = grants.createProfile(tenant, "P", "Profile"); UUID group = grants.createGroup(tenant, "G", "Group");
        grants.grant(JdbcGrantService.Target.PROFILE, tenant, profile, "ALTERAR_DEMO"); grants.link(JdbcGrantService.Link.GROUP_PROFILE, tenant, group, profile);
        var restricted = new JdbcGrantService(jdbc, tx, events, CATALOG, () -> Set.of("CONSULTAR_DEMO"), CLOCK,
                () -> new br.com.gems.firebase.auth.SecurityActorProvider.Actor(actor, false, Optional.of("alpha")));
        assertThatThrownBy(() -> restricted.link(JdbcGrantService.Link.MEMBER_PROFILE, tenant, mr, profile)).isInstanceOf(FirebaseAuthException.class);
        assertThatThrownBy(() -> restricted.link(JdbcGrantService.Link.GROUP_PROFILE, tenant, group, profile)).isInstanceOf(FirebaseAuthException.class);
        assertThatThrownBy(() -> restricted.link(JdbcGrantService.Link.GROUP_MEMBER, tenant, group, mr)).isInstanceOf(FirebaseAuthException.class);
        UUID unknown = UUID.randomUUID();
        execute("INSERT INTO security.role_seguranca(id_role,cd_acao,cd_escopo,dt_inicio) VALUES(?,'DESCONHECIDA_ACAO','TENANT',?)", unknown, SecurityJdbcSupport.time(NOW));
        UUID drift = grants.createProfile(tenant, "DRIFT", "Drift");
        execute("INSERT INTO security.perfil_role(id_perfil_role,id_tenant,id_perfil,id_role,dt_inicio) VALUES(?,?,?,?,?)", UUID.randomUUID(), tenant, drift, unknown, SecurityJdbcSupport.time(NOW));
        assertThatThrownBy(() -> grants.link(JdbcGrantService.Link.MEMBER_PROFILE, tenant, mr, drift)).isInstanceOf(FirebaseAuthException.class);
    }
    @Test void directLocalTenantAndProjectRefusalsAreAuditedWithoutSensitiveInput() {
        var captured = new java.util.ArrayList<SecurityEventSink.Event>(); UUID user = bound("refused");
        var audited = new JdbcAuthorizationResolver(jdbc, tx, captured::add, CATALOG, new StrictTenantAliasValidator(), PROJECT, 2);
        var token = identity("refused", "refused@example.test", "google.com", true);
        assertThatThrownBy(() -> audited.resolve(token, Optional.of("sensitive-alias"), NOW)).isInstanceOf(FirebaseAuthException.class);
        assertThatThrownBy(() -> audited.resolve(new VerifiedIdentity("demo-other", "https://securetoken.google.com/demo-other", "sensitive-uid", "sensitive-email", true, "google.com", "Secret", NOW), Optional.empty(), NOW)).isInstanceOf(FirebaseAuthException.class);
        users.suspend(user); assertThatThrownBy(() -> audited.resolve(token, Optional.empty(), NOW)).isInstanceOf(FirebaseAuthException.class);
        assertThat(captured).hasSize(3);
        assertThat(captured).allMatch(event -> event.type().equals("LOCAL_OR_TENANT_ACCESS_REFUSED") && event.correlationId() != null);
        assertThat(captured.toString()).doesNotContain("sensitive-alias", "sensitive-uid", "sensitive-email", "Secret", "refused@example.test");
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"CREATE", "UPDATE"})
    void candidateCreateOrUpdateRacingGoogleLinkCannotBypassUniqueEligibility(String mutation) throws Exception {
        String email = "candidate-race@example.test"; UUID original = users.preProvisionGoogle("Original", email);
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")) {
                statement.setString(1, email); statement.execute();
            }
            try (var pool = Executors.newFixedThreadPool(2)) {
                try {
                    var changed = pool.submit(() -> {
                        if (mutation.equals("CREATE")) { return users.preProvisionGoogle("Concurrent", email); }
                        users.update(original, "Updated", "new-candidate-race@example.test"); return original;
                    });
                    var linked = pool.submit(() -> {
                        try { return resolver.resolve(identity("race-candidate", email, "google.com", true), Optional.empty(), NOW).principal().userId(); }
                        catch (FirebaseAuthException denied) { return null; }
                    });
                    org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                            assertThat(jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%pg_advisory_xact_lock%'").query(Long.class).single()).isGreaterThanOrEqualTo(2));
                    connection.commit(); UUID candidate = changed.get(15, TimeUnit.SECONDS); UUID owner = linked.get(15, TimeUnit.SECONDS);
                    if (owner != null) { assertThat(owner).isEqualTo(original); }
                    assertThat(count("usuario_identidade")).isEqualTo(owner == null ? 0 : 1);
                    if (mutation.equals("CREATE")) {
                        assertThat(candidate).isNotEqualTo(original);
                        assertThat(jdbc.sql("SELECT cd_elegibilidade FROM security.usuario WHERE id_usuario=:id").param("id", candidate).query(String.class).single()).isEqualTo("GOOGLE_PENDING");
                    } else {
                        assertThat(jdbc.sql("SELECT cd_email FROM security.usuario WHERE id_usuario=:id").param("id", original).query(String.class).single()).isEqualTo("new-candidate-race@example.test");
                    }
                } finally { connection.rollback(); }
            }
        }
    }
}
