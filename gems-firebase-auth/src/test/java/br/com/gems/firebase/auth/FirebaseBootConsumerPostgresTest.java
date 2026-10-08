package br.com.gems.firebase.auth;

import br.com.gems.firebase.auth.consumer.BusinessRecord;
import br.com.gems.firebase.auth.consumer.BusinessRecordRepository;
import br.com.gems.firebase.auth.jdbc.JdbcAuthorizationResolver;
import br.com.gems.firebase.auth.jdbc.JdbcIdentityCommandWorker;
import br.com.gems.firebase.auth.jdbc.JdbcSecurityCatalog;
import br.com.gems.firebase.auth.jdbc.JdbcSecurityEventOutbox;
import br.com.gems.firebase.auth.jdbc.JdbcTenantProvisioner;
import br.com.gems.security.authorization.AuthorizationCatalog;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Consumer Boot 4 real: pool automático ou proxy consumidor; EMF/manager empresariais automáticos. */
@Testcontainers
class FirebaseBootConsumerPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final AuthorizationCatalog CATALOG = new AuthorizationCatalog(Set.of("CONSULTAR_PROPRIA_CONTA"), Set.of("CONSULTAR_DEMO"));
    final List<SecurityEventSink.Event> emitted = java.util.Collections.synchronizedList(new ArrayList<>());
    JdbcClient outside;
    PGSimpleDataSource migrationSource;

    @BeforeEach void migrateBeforeConsumerStartup() throws Exception {
        // Esta conexão aplica explicitamente o master reutilizável. Ela nunca é bean do consumer.
        migrationSource = new PGSimpleDataSource();
        migrationSource.setURL(POSTGRES.getJdbcUrl()); migrationSource.setUser(POSTGRES.getUsername()); migrationSource.setPassword(POSTGRES.getPassword());
        outside = JdbcClient.create(migrationSource);
        outside.sql("DROP SCHEMA IF EXISTS security CASCADE").update();
        outside.sql("CREATE SCHEMA security").update();
        outside.sql("DROP TABLE IF EXISTS public.firebase_business_record").update();
        outside.sql("CREATE TABLE public.firebase_business_record(id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL)").update();
        outside.sql("CREATE SCHEMA IF NOT EXISTS firebase_business_scope").update();
        try (var connection = migrationSource.getConnection()) {
            var database = liquibase.database.DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new liquibase.database.jvm.JdbcConnection(connection));
            database.setDefaultSchemaName("security");
            try (var liquibase = new liquibase.Liquibase("db/changelog/gems-firebase-auth/master.xml", new liquibase.resource.ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new liquibase.Contexts(), new liquibase.LabelExpression());
            }
        }
    }
    WebApplicationContextRunner runner(boolean jpa) {
        var configurations = new ArrayList<Class<?>>(List.of(FirebaseMvcIntegrationTest.imports()));
        configurations.addAll(List.of(DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
                JdbcClientAutoConfiguration.class, TransactionAutoConfiguration.class));
        if (jpa) { configurations.addAll(List.of(HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class)); }
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(configurations.toArray(Class<?>[]::new)))
                .withUserConfiguration(ConsumerConfiguration.class)
                .withPropertyValues("gems.firebase.auth.enabled=true", "gems.firebase.auth.jdbc-enabled=true", "gems.firebase.auth.project-id=stub-gems",
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(), "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(), "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false")
                .withBean(Clock.class, () -> CLOCK).withBean(SecurityEventSink.class, () -> emitted::add);
    }
    VerifiedIdentity identity(String uid) {
        return new VerifiedIdentity("stub-gems", "https://securetoken.google.com/stub-gems", uid, uid + "@example.test", true, "google.com", "Person", NOW);
    }
    void allJdbcBeans(org.springframework.context.ApplicationContext context) {
        assertThat(context.getBean("dataSource")).isInstanceOf(com.zaxxer.hikari.HikariDataSource.class);
        assertThat(context.getBean(AuthorizationResolver.class)).isInstanceOf(JdbcAuthorizationResolver.class);
        for (var type : List.of(JdbcSecurityCatalog.class, UserAdministration.class, TenantAdministration.class, GrantAdministration.class,
                SecurityAdministrationReader.class, TenantAdministratorBootstrap.class, JdbcSecurityEventOutbox.class, JdbcIdentityCommandWorker.class, JdbcTenantProvisioner.class)) {
            assertThat(context.getBeansOfType(type)).hasSize(1);
        }
        assertThat(context.getBean(FirebaseAuthenticationFilter.class)).isNotNull();
        assertThat(context.getBean("firebaseEndpointAuthorizationScan")).isNotNull();
        assertThat(context.getBean("firebaseSecurityTransactions", TransactionTemplate.class).getTransactionManager())
                .isSameAs(context.getBean("firebaseSecurityTransactionManager"));
        assertThat(outside.sql("SELECT count(*) FROM security.databasechangelog").query(Long.class).single()).isPositive();
        assertThat(outside.sql("SELECT cd_acao FROM security.role_seguranca").query(String.class).set()).containsAll(CATALOG.globalActions()).containsAll(CATALOG.tenantActions());
    }
    long count(String table) { return outside.sql("SELECT count(*) FROM security." + table).query(Long.class).single(); }
    long rawCount(String sql, UUID id) {
        try (var connection = migrationSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
        } catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
    }
    void assertCleanThread() {
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test void bootCreatedDataSourceActivatesCompleteJdbcConsumerAndDefaultManager() {
        runner(false).withUserConfiguration(JdbcBoundaryConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed(); allJdbcBeans(context);
            var businessManager = context.getBean("transactionManager", PlatformTransactionManager.class);
            assertThat(businessManager).isInstanceOf(JdbcTransactionManager.class).isNotSameAs(context.getBean("firebaseSecurityTransactionManager"));
            assertThat(context.getBean(JdbcBoundary.class).manager()).isSameAs(businessManager);
            var users = context.getBean(UserAdministration.class);
            UUID retained = users.preProvisionGoogle("Before", "before@example.test");
            UUID businessId = UUID.randomUUID();
            assertThatThrownBy(() -> context.getBean(JdbcBoundary.class).execute(() -> {
                context.getBean("jdbcClient", JdbcClient.class).sql("INSERT INTO public.firebase_business_record VALUES(:id,'business rollback')").param("id", businessId).update();
                users.update(retained, "Security commit", "before@example.test");
                throw new BusinessRollback();
            })).isInstanceOf(BusinessRollback.class);
            assertThat(outside.sql("SELECT count(*) FROM public.firebase_business_record WHERE id=:id").param("id", businessId).query(Long.class).single()).isZero();
            assertThat(context.getBean(SecurityAdministrationReader.class).findUser(retained).orElseThrow().name()).isEqualTo("Security commit");
            assertCleanThread();
        });
    }
    @Test void bootJpaManagerAndUnqualifiedRepositoryTransactionsCommitAndRollback() {
        runner(true).withUserConfiguration(JpaBoundaryConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed(); allJdbcBeans(context);
            assertThat(context.getBean("transactionManager")).isInstanceOf(JpaTransactionManager.class);
            assertThat(context.getBeansOfType(JpaTransactionManager.class)).hasSize(1);
            assertThat(context.getBean(JpaBoundary.class).manager()).isSameAs(context.getBean("transactionManager"));
            assertThat(context.getBean("firebaseSecurityTransactionManager")).isInstanceOf(JdbcTransactionManager.class);
            var business = context.getBean(JpaBoundary.class); var repo = context.getBean(BusinessRecordRepository.class);
            UUID committed = UUID.randomUUID(); UUID rolledBack = UUID.randomUUID(); UUID repositoryCommit = UUID.randomUUID();
            business.write(committed, "committed", r -> { }, false);
            assertThat(business.read(committed).orElseThrow().getName()).isEqualTo("committed");
            assertThatThrownBy(() -> business.write(rolledBack, "rolled back", r -> { }, true)).isInstanceOf(BusinessRollback.class);
            assertThat(business.read(rolledBack)).isEmpty();
            repo.saveAndFlush(new BusinessRecord(repositoryCommit, "repository default"));
            assertThat(repo.findById(repositoryCommit).orElseThrow().getName()).isEqualTo("repository default");
            UUID user = context.getBean(UserAdministration.class).preProvisionGoogle("After", "after@example.test");
            assertThat(context.getBean(AuthorizationResolver.class).resolve(identity("after"), Optional.empty(), NOW).principal().userId()).isEqualTo(user);
            assertCleanThread();
        });
    }
    @Test void securityInsideJpaCommitsSuspensionAndOutboxIndependentlyAndRestoresBusinessResources() {
        runner(true).withUserConfiguration(JpaBoundaryConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            var users = context.getBean(UserAdministration.class); var resolver = context.getBean(AuthorizationResolver.class);
            UUID user = users.preProvisionGoogle("Person", "nested@example.test");
            assertThat(resolver.resolve(identity("nested"), Optional.empty(), NOW).principal().userId()).isEqualTo(user);
            var source = context.getBean("dataSource", DataSource.class); var emf = context.getBean(EntityManagerFactory.class);
            UUID businessId = UUID.randomUUID(); int eventsBefore = emitted.size();
            assertThatThrownBy(() -> context.getBean(JpaBoundary.class).write(businessId, "rollback", repo -> {
                var connectionHolder = TransactionSynchronizationManager.getResource(source);
                var entityHolder = TransactionSynchronizationManager.getResource(emf);
                var synchronizations = TransactionSynchronizationManager.getSynchronizations();
                var businessJdbc = JdbcClient.create(source);
                businessJdbc.sql("SET LOCAL search_path TO firebase_business_scope").update();
                assertThat(businessJdbc.sql("SELECT current_schema()").query(String.class).single()).isEqualTo("firebase_business_scope");
                assertThat(context.getBean(SecurityAdministrationReader.class).findUser(user)).isPresent();
                assertThat(resolver.resolve(identity("nested"), Optional.empty(), NOW).context().globalActions()).contains("CONSULTAR_PROPRIA_CONTA");
                users.suspend(user);
                assertThat(emitted).hasSize(eventsBefore + 1);
                assertThat(rawCount("SELECT count(*) FROM security.usuario WHERE id_usuario=? AND dt_fim IS NOT NULL", user)).isEqualTo(1);
                assertThat(rawCount("SELECT count(*) FROM security.comando_identidade WHERE id_usuario=? AND cd_tipo IN ('DISABLE','REVOKE')", user)).isEqualTo(2);
                assertThat(TransactionSynchronizationManager.getResource(source)).isSameAs(connectionHolder);
                assertThat(TransactionSynchronizationManager.getResource(emf)).isSameAs(entityHolder);
                assertThat(TransactionSynchronizationManager.getSynchronizations()).containsExactlyElementsOf(synchronizations);
                assertThat(businessJdbc.sql("SELECT current_schema()").query(String.class).single()).isEqualTo("firebase_business_scope");
                assertThat(repo.findById(businessId)).isPresent();
            }, true)).isInstanceOf(BusinessRollback.class);
            assertThat(context.getBean(JpaBoundary.class).read(businessId)).isEmpty();
            assertThat(context.getBean(SecurityAdministrationReader.class).findUser(user).orElseThrow().endsAt()).isNotNull();
            assertThatThrownBy(() -> resolver.resolve(identity("nested"), Optional.empty(), NOW)).isInstanceOf(FirebaseAuthException.class);
            assertThat(count("evento_seguranca")).isEqualTo(count("entrega_evento"));
            assertThat(outside.sql("SELECT count(*) FROM security.usuario_role_global WHERE id_usuario=:id AND dt_fim IS NOT NULL").param("id", user).query(Long.class).single()).isEqualTo(1);
            context.getBean(JpaBoundary.class).write(UUID.randomUUID(), "after rollback", repo -> { }, false);
            assertCleanThread();
        });
    }
    @Test void failedSecurityWriteRollsBackItsRowsAndEventsWhileBusinessJpaCanCommit() {
        runner(true).withUserConfiguration(JpaBoundaryConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            outside.sql("CREATE FUNCTION security.reject_test_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test event failure'; END $$").update();
            outside.sql("CREATE TRIGGER reject_test_event BEFORE INSERT ON security.evento_seguranca FOR EACH ROW EXECUTE FUNCTION security.reject_test_event()").update();
            UUID businessId = UUID.randomUUID(); long eventsBefore = count("evento_seguranca"); long deliveriesBefore = count("entrega_evento");
            context.getBean(JpaBoundary.class).write(businessId, "business survives", repo -> {
                assertThatThrownBy(() -> context.getBean(UserAdministration.class).preProvisionGoogle("Fail", "fail@example.test"))
                        .isInstanceOf(org.springframework.dao.DataAccessException.class);
                assertThat(repo.findById(businessId)).isPresent();
            }, false);
            assertThat(context.getBean(JpaBoundary.class).read(businessId).orElseThrow().getName()).isEqualTo("business survives");
            assertThat(count("usuario")).isZero(); assertThat(count("usuario_role_global")).isZero();
            assertThat(count("evento_seguranca")).isEqualTo(eventsBefore); assertThat(count("entrega_evento")).isEqualTo(deliveriesBefore);
            assertThat(emitted).isEmpty(); assertCleanThread();
        });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void namedConsumerSecurityManagerOverrideBacksOffWithoutSuppressingBootBusinessManager(boolean jpa) {
        runner(jpa).withUserConfiguration(jpa ? JpaBoundaryConfiguration.class : JdbcBoundaryConfiguration.class, SecurityManagerOverride.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("firebaseSecurityTransactionManager")).isExactlyInstanceOf(ConsumerSecurityManager.class);
            assertThat(context.getBean("transactionManager")).isInstanceOf(jpa ? JpaTransactionManager.class : JdbcTransactionManager.class);
            assertThat(context.getBean(QualifiedBoundary.class).manager()).isSameAs(context.getBean("firebaseSecurityTransactionManager"));
            assertThat(context.getBean("firebaseSecurityTransactions", TransactionTemplate.class).getTransactionManager())
                    .isSameAs(context.getBean("firebaseSecurityTransactionManager"));
            context.getBean(QualifiedBoundary.class).execute(() -> assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue());
            var users = context.getBean(UserAdministration.class);
            UUID user = users.preProvisionGoogle("Custom", "custom@example.test");
            assertThat(context.getBean(SecurityAdministrationReader.class).findUser(user)).isPresent();
            if (!jpa) {
                UUID businessId = UUID.randomUUID();
                assertThat(context.getBean(JdbcBoundary.class).manager()).isSameAs(context.getBean("transactionManager"));
                assertThatThrownBy(() -> context.getBean(JdbcBoundary.class).execute(() -> {
                    context.getBean("jdbcClient", JdbcClient.class).sql("INSERT INTO public.firebase_business_record VALUES(:id,'rollback')").param("id", businessId).update();
                    users.update(user, "Custom security commit", "custom@example.test");
                    throw new BusinessRollback();
                })).isInstanceOf(BusinessRollback.class);
                assertThat(outside.sql("SELECT count(*) FROM public.firebase_business_record WHERE id=:id").param("id", businessId).query(Long.class).single()).isZero();
                assertThat(context.getBean(SecurityAdministrationReader.class).findUser(user).orElseThrow().name()).isEqualTo("Custom security commit");
            }
            assertCleanThread();
        });
    }
    @Test void nestedSecurityOperationsRemainOneAtomicUnitWithTheirQualifiedManager() {
        runner(true).run(context -> {
            assertThat(context).hasNotFailed();
            var security = context.getBean("firebaseSecurityTransactions", TransactionTemplate.class);
            assertThatThrownBy(() -> security.executeWithoutResult(status -> {
                UUID user = context.getBean(UserAdministration.class).preProvisionGoogle("Nested", "atomic@example.test");
                assertThat(context.getBean(SecurityAdministrationReader.class).findUser(user)).isPresent();
                throw new BusinessRollback();
            })).isInstanceOf(BusinessRollback.class);
            assertThat(count("usuario")).isZero(); assertThat(count("usuario_role_global")).isZero();
            assertThat(count("evento_seguranca")).isZero(); assertThat(count("entrega_evento")).isZero();
            assertThat(emitted).isEmpty(); assertCleanThread();
        });
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void transactionAwareConsumerKeepsPhysicalSecurityCommitIndependentOfJpaRollback(int adapters) {
        try (var pool = consumerPool()) {
            var consumer = proxyRunner(pool, adapters);
            if (adapters == 3) {
                consumer = consumer.withUserConfiguration(SecurityManagerOverride.class)
                        .withBean("firebaseSecurityDataSource", DataSource.class, () -> new org.springframework.jdbc.datasource.DelegatingDataSource(pool),
                                definition -> ((org.springframework.beans.factory.support.AbstractBeanDefinition) definition).setDefaultCandidate(false));
            }
            consumer.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean("transactionManager")).isInstanceOf(JpaTransactionManager.class);
                assertThat(context.getBeansOfType(JpaTransactionManager.class)).hasSize(1);
                assertThat(context.getBean(JpaBoundary.class).manager()).isSameAs(context.getBean("transactionManager"));
                var emf = context.getBean(EntityManagerFactory.class);
                var users = context.getBean(UserAdministration.class);
                var security = context.getBean("firebaseSecurityTransactions", TransactionTemplate.class);
                var securityJdbc = context.getBean("firebaseSecurityJdbcClient", JdbcClient.class);
                UUID user = users.preProvisionGoogle("Proxy", "proxy@example.test");
                context.getBean(AuthorizationResolver.class).resolve(identity("proxy"), Optional.empty(), NOW);
                UUID businessId = UUID.randomUUID();
                assertThatThrownBy(() -> context.getBean(JpaBoundary.class).write(businessId, "rollback", repo -> {
                    var resources = new java.util.HashMap<>(TransactionSynchronizationManager.getResourceMap());
                    var em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
                    em.createNativeQuery("SET LOCAL search_path TO firebase_business_scope").executeUpdate();
                    int businessPid = ((Number) em.createNativeQuery("SELECT pg_backend_pid()").getSingleResult()).intValue();
                    security.executeWithoutResult(status -> {
                        int securityPid = securityJdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single();
                        assertThat(securityPid).isNotEqualTo(businessPid);
                        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(2);
                        assertThat(securityJdbc.sql("SELECT current_schema()").query(String.class).single()).isEqualTo("public");
                        users.suspend(user);
                        assertThat(context.getBean(SecurityAdministrationReader.class).findUser(user).orElseThrow().endsAt()).isNotNull();
                        assertThat(securityJdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single()).isEqualTo(securityPid);
                    });
                    assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
                    assertThat(TransactionSynchronizationManager.getResourceMap()).isEqualTo(resources);
                    assertThat(em.createNativeQuery("SELECT current_schema()").getSingleResult()).isEqualTo("firebase_business_scope");
                    assertThat(rawCount("SELECT count(*) FROM security.usuario WHERE id_usuario=? AND dt_fim IS NOT NULL", user)).isEqualTo(1);
                    assertThat(rawCount("SELECT count(*) FROM security.comando_identidade WHERE id_usuario=? AND cd_tipo IN ('DISABLE','REVOKE')", user)).isEqualTo(2);
                    assertThat(rawCount("SELECT count(*) FROM security.evento_seguranca e JOIN security.entrega_evento d USING(id_evento) WHERE e.id_usuario=? AND e.cd_evento='BOOTSTRAP_USER_SUSPENDED'", user)).isEqualTo(1);
                }, true)).isInstanceOf(BusinessRollback.class);
                assertThat(rawCount("SELECT count(*) FROM public.firebase_business_record WHERE id=?", businessId)).isZero();
                assertThat(rawCount("SELECT count(*) FROM security.usuario_role_global WHERE id_usuario=? AND dt_fim IS NOT NULL", user)).isEqualTo(1);
                assertThat(count("evento_seguranca")).isEqualTo(count("entrega_evento"));
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
                assertCleanThread();
                UUID committed = UUID.randomUUID();
                context.getBean(JpaBoundary.class).write(committed, "later commit", repo -> { }, false);
                assertThat(rawCount("SELECT count(*) FROM public.firebase_business_record WHERE id=?", committed)).isEqualTo(1);
                try (var connection = pool.getConnection()) { assertThat(connection.getSchema()).isEqualTo("public"); }
                catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
            });
            assertThat(pool.isClosed()).isFalse(); // SDK wrappers do not own/close the consumer pool.
        }
    }
    @Test void transactionAwareFailureCleansSecurityResourcesAndBusinessJpaStillCommits() {
        try (var pool = consumerPool()) {
            proxyRunner(pool, 2).run(context -> {
                assertThat(context).hasNotFailed();
                outside.sql("CREATE FUNCTION security.reject_test_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test event failure'; END $$").update();
                outside.sql("CREATE TRIGGER reject_test_event BEFORE INSERT ON security.evento_seguranca FOR EACH ROW EXECUTE FUNCTION security.reject_test_event()").update();
                UUID businessId = UUID.randomUUID();
                context.getBean(JpaBoundary.class).write(businessId, "survives", repo -> {
                    var resources = new java.util.HashMap<>(TransactionSynchronizationManager.getResourceMap());
                    assertThatThrownBy(() -> context.getBean(UserAdministration.class).preProvisionGoogle("Fail", "failure@example.test"))
                            .isInstanceOf(org.springframework.dao.DataAccessException.class);
                    assertThat(TransactionSynchronizationManager.getResourceMap()).isEqualTo(resources);
                    assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
                    assertThat(repo.findById(businessId)).isPresent();
                }, false);
                assertThat(rawCount("SELECT count(*) FROM public.firebase_business_record WHERE id=?", businessId)).isEqualTo(1);
                assertThat(count("usuario")).isZero(); assertThat(count("usuario_role_global")).isZero();
                assertThat(count("evento_seguranca")).isZero(); assertThat(count("entrega_evento")).isZero();
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero(); assertCleanThread();
            });
        }
    }
    @Test void boundedPoolConcurrentProxyConsumersKeepTwoIndependentConnectionsEach() throws Exception {
        try (var pool = consumerPool(); var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            proxyRunner(pool, 2).run(context -> {
                assertThat(context).hasNotFailed();
                var users = context.getBean(UserAdministration.class);
                var business = context.getBean(JpaBoundary.class);
                var security = context.getBean("firebaseSecurityTransactions", TransactionTemplate.class);
                var jdbc = context.getBean("firebaseSecurityJdbcClient", JdbcClient.class);
                var entered = new java.util.concurrent.CountDownLatch(2);
                var release = new java.util.concurrent.CountDownLatch(1);
                var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
                var userIds = List.of(users.preProvisionGoogle("One", "one@example.test"), users.preProvisionGoogle("Two", "two@example.test"));
                var futures = new ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < 2; i++) {
                    int index = i;
                    futures.add(executor.submit(() -> {
                        assertThatThrownBy(() -> business.write(ids.get(index), "rollback", repo -> {
                            var em = EntityManagerFactoryUtils.getTransactionalEntityManager(context.getBean(EntityManagerFactory.class));
                            int businessPid = ((Number) em.createNativeQuery("SELECT pg_backend_pid()").getSingleResult()).intValue();
                            security.executeWithoutResult(status -> {
                                assertThat(jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single()).isNotEqualTo(businessPid);
                                entered.countDown();
                                try { assertThat(release.await(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                                users.suspend(userIds.get(index));
                            });
                        }, true)).isInstanceOf(BusinessRollback.class);
                        assertCleanThread();
                    }));
                }
                try {
                    assertThat(entered.await(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(4);
                } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                finally { release.countDown(); }
                for (var future : futures) {
                    try { future.get(30, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }
                for (int i = 0; i < 2; i++) {
                    assertThat(rawCount("SELECT count(*) FROM public.firebase_business_record WHERE id=?", ids.get(i))).isZero();
                    assertThat(rawCount("SELECT count(*) FROM security.usuario WHERE id_usuario=? AND dt_fim IS NOT NULL", userIds.get(i))).isEqualTo(1);
                }
                assertThat(count("evento_seguranca")).isEqualTo(count("entrega_evento"));
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero(); assertCleanThread();
            });
        }
    }
    com.zaxxer.hikari.HikariDataSource consumerPool() {
        var pool = new com.zaxxer.hikari.HikariDataSource();
        pool.setJdbcUrl(POSTGRES.getJdbcUrl()); pool.setUsername(POSTGRES.getUsername()); pool.setPassword(POSTGRES.getPassword());
        pool.setMaximumPoolSize(4); pool.setMinimumIdle(0); pool.setConnectionTimeout(10000);
        return pool;
    }
    WebApplicationContextRunner proxyRunner(com.zaxxer.hikari.HikariDataSource pool, int adapters) {
        DataSource source = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(pool);
        if (adapters >= 1) { source = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(source); }
        if (adapters >= 2) { source = new org.springframework.jdbc.datasource.DelegatingDataSource(source); }
        if (adapters == 3) {
            source = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(new org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy(pool));
        }
        DataSource businessSource = source;
        return runner(true).withUserConfiguration(JpaBoundaryConfiguration.class)
                .withBean("dataSource", DataSource.class, () -> businessSource);
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackageClasses = BusinessRecord.class)
    static class ConsumerConfiguration {
        @Bean AuthorizationCatalog catalog() { return CATALOG; }
        @Bean IdTokenVerifier verifier() { return value -> { throw FirebaseAuthException.denied(); }; }
        @Bean FirebaseAdminGateway admin() { return mock(FirebaseAdminGateway.class); }
        @Bean SecurityActorProvider actors() { return SecurityActorProvider.trustedBootstrap(new UUID(0, 1)); }
    }
    @Configuration(proxyBeanMethods = false)
    static class JdbcBoundaryConfiguration {
        @Bean JdbcBoundary jdbcBoundary(PlatformTransactionManager manager) { return new JdbcBoundary(manager); }
    }
    static class JdbcBoundary {
        final PlatformTransactionManager manager;
        JdbcBoundary(PlatformTransactionManager manager) { this.manager = manager; }
        public PlatformTransactionManager manager() { return manager; }
        @Transactional public void execute(Runnable body) { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue(); body.run(); }
    }
    @Configuration(proxyBeanMethods = false)
    static class JpaBoundaryConfiguration {
        @Bean JpaBoundary jpaBoundary(BusinessRecordRepository repo, EntityManagerFactory emf, PlatformTransactionManager manager) { return new JpaBoundary(repo, emf, manager); }
    }
    static class JpaBoundary {
        final BusinessRecordRepository repo; final EntityManagerFactory emf; final PlatformTransactionManager manager;
        JpaBoundary(BusinessRecordRepository repo, EntityManagerFactory emf, PlatformTransactionManager manager) { this.repo = repo; this.emf = emf; this.manager = manager; }
        public PlatformTransactionManager manager() { return manager; }
        @Transactional public void write(UUID id, String name, Consumer<BusinessRecordRepository> body, boolean rollback) {
            assertThat(EntityManagerFactoryUtils.getTransactionalEntityManager(emf)).isNotNull();
            assertThat(EntityManagerFactoryUtils.getTransactionalEntityManager(emf).getTransaction().isActive()).isTrue();
            repo.saveAndFlush(new BusinessRecord(id, name)); body.accept(repo);
            if (rollback) { throw new BusinessRollback(); }
        }
        @Transactional(readOnly = true) public Optional<BusinessRecord> read(UUID id) { return repo.findById(id); }
    }
    static class BusinessRollback extends RuntimeException { }
    static class ConsumerSecurityManager extends JdbcTransactionManager {
        ConsumerSecurityManager(DataSource source) { super(source); }
    }
    @Configuration(proxyBeanMethods = false)
    static class SecurityManagerOverride {
        @Bean(name = "firebaseSecurityTransactionManager", defaultCandidate = false)
        ConsumerSecurityManager securityManager(@Qualifier("firebaseSecurityDataSource") DataSource source) { return new ConsumerSecurityManager(source); }
        @Bean QualifiedBoundary qualifiedBoundary(@Qualifier("firebaseSecurityTransactionManager") PlatformTransactionManager manager) { return new QualifiedBoundary(manager); }
    }
    static class QualifiedBoundary {
        final PlatformTransactionManager manager;
        QualifiedBoundary(PlatformTransactionManager manager) { this.manager = manager; }
        public PlatformTransactionManager manager() { return manager; }
        @Transactional("firebaseSecurityTransactionManager") public void execute(Runnable body) { body.run(); }
    }
}
