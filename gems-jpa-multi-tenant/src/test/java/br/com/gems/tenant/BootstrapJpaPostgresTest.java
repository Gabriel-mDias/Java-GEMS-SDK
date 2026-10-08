package br.com.gems.tenant;

import br.com.gems.tenant.bootstrapfixture.DemoRecord;
import br.com.gems.tenant.bootstrapfixture.DemoRepository;
import br.com.gems.tenant.config.MultiTenantJpaConfig;
import br.com.gems.tenant.migration.TenantMigrationCoordinator;
import br.com.gems.tenant.migration.TenantSchemaSource;
import br.com.gems.tenant.service.TenantSchemaService;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class BootstrapJpaPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @AfterEach void clear() { JpaTenantContext.clear(); }
    @Test void migrationsPrecedeDdlValidateRepositoriesInitializeAndBusinessSqlFailsClosed() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MultiTenantJpaConfig.class)).withUserConfiguration(Fixture.class)
                .withPropertyValues("gems.tenant.enabled=true", "gems.tenant.strict-alias=true", "gems.tenant.validation-schema=modelo",
                        "gems.tenant.global-schema=administracao", "gems.tenant.liquibase.changelog=classpath:bootstrap-test-changelog.xml",
                        "gems.tenant.liquibase.startup-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DemoRepository.class).doesNotHaveBean(TenantMigrationCoordinator.class);
                    assertThat(JpaTenantContext.current()).isEmpty();
                    var jdbc = context.getBean(JdbcClient.class);
                    assertThat(jdbc.sql("SELECT count(*) FROM modelo.databasechangelog WHERE id='bootstrap-fixture-1'").query(Long.class).single()).isEqualTo(1);
                    var repository = context.getBean(DemoRepository.class);
                    assertThatThrownBy(repository::findAll).hasRootCauseInstanceOf(TenantContextMissingException.class);
                    var provisioning = context.getBean(TenantSchemaService.class);
                    provisioning.createSchemaAndRunLiquibase("alpha"); provisioning.createSchemaAndRunLiquibase("beta");
                    UUID id = UUID.randomUUID();
                    try (var scope = TenantScope.forTenant("alpha")) { repository.saveAndFlush(new DemoRecord(id, "Alpha")); }
                    try (var scope = TenantScope.forTenant("beta")) { repository.saveAndFlush(new DemoRecord(id, "Beta")); }
                    try (var scope = TenantScope.forTenant("alpha")) { assertThat(repository.findById(id).orElseThrow().getName()).isEqualTo("Alpha"); }
                    try (var scope = TenantScope.forTenant("beta")) { assertThat(repository.findById(id).orElseThrow().getName()).isEqualTo("Beta"); }
                    assertThat(JpaTenantContext.current()).isEmpty();
                    try (var connection = context.getBean(DataSource.class).getConnection()) { assertThat(connection.getSchema()).isEqualTo("public"); }
                });
    }
    @Configuration(proxyBeanMethods = false)
    @EnableJpaRepositories(basePackageClasses = DemoRepository.class)
    static class Fixture {
        @Bean DataSource dataSource() {
            var source = new PGSimpleDataSource(); source.setURL(POSTGRES.getJdbcUrl());
            source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword()); return source;
        }
        @Bean JdbcClient jdbcClient(DataSource dataSource) { return JdbcClient.create(dataSource); }
        @Bean TenantSchemaSource tenantSchemaSource() { return () -> Set.of("tenant_suspended_ready"); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, HibernatePropertiesCustomizer customizer) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(dataSource);
            factory.setPackagesToScan(DemoRecord.class.getPackageName()); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            var properties = new HashMap<String, Object>(); properties.put("hibernate.hbm2ddl.auto", "validate");
            customizer.customize(properties); factory.setJpaPropertyMap(properties); return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
    }
}
