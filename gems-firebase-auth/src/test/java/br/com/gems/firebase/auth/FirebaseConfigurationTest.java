package br.com.gems.firebase.auth;

import br.com.gems.firebase.auth.config.FirebaseAuthAutoConfiguration;
import br.com.gems.firebase.auth.config.FirebaseJdbcAutoConfiguration;
import br.com.gems.firebase.auth.config.FirebaseServletAutoConfiguration;
import br.com.gems.security.authorization.JwtAuthorizationContext;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirebaseConfigurationTest {
    @Test void optInCustomPortsWorkWithoutDataSourceAndNeverRegisterContainerFilter() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(FirebaseAuthAutoConfiguration.class,
                FirebaseJdbcAutoConfiguration.class, FirebaseServletAutoConfiguration.class))
                .withPropertyValues("gems.firebase.auth.enabled=true", "gems.firebase.auth.project-id=stub-gems")
                .withBean(br.com.gems.security.authorization.AuthorizationCatalog.class, () -> new br.com.gems.security.authorization.AuthorizationCatalog(Set.of(), Set.of()))
                .withBean(IdTokenVerifier.class, () -> token -> { throw FirebaseAuthException.denied(); })
                .withBean(FirebaseAdminGateway.class, () -> mock(FirebaseAdminGateway.class))
                .withBean(AuthorizationResolver.class, () -> (id, alias, now) -> new AuthorizationResolver.Resolution(
                        new FirebasePrincipal(UUID.randomUUID(), id.uid(), "Person", "person@example.test"), JwtAuthorizationContext.global(Set.of(), Set.of())))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(FirebaseAuthenticationFilter.class);
                    assertThat(context).doesNotHaveBean(com.google.firebase.FirebaseApp.class);
                    assertThat(context).doesNotHaveBean(javax.sql.DataSource.class);
                    assertThat(context.getBean(FilterRegistrationBean.class).isEnabled()).isFalse();
                });
    }
    @Test void moduleIsInactiveWithoutExplicitFlag() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(FirebaseAuthAutoConfiguration.class,
                FirebaseJdbcAutoConfiguration.class, FirebaseServletAutoConfiguration.class))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(FirebaseAuthenticationFilter.class));
    }
    @Test void individualPortOverridesKeepConsumerFirebaseAppAlive() {
        var app = com.google.firebase.FirebaseApp.initializeApp(com.google.firebase.FirebaseOptions.builder().setProjectId("stub-gems")
                .setCredentials(com.google.auth.oauth2.GoogleCredentials.create(new com.google.auth.oauth2.AccessToken("transport-test-only", new java.util.Date(Long.MAX_VALUE))))
                .build(), "consumer-test-" + UUID.randomUUID());
        try {
            var runner = new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(FirebaseAuthAutoConfiguration.class))
                    .withPropertyValues("gems.firebase.auth.enabled=true", "gems.firebase.auth.project-id=stub-gems")
                    .withBean(br.com.gems.security.authorization.AuthorizationCatalog.class, () -> new br.com.gems.security.authorization.AuthorizationCatalog(Set.of(), Set.of()))
                    .withBean(com.google.firebase.FirebaseApp.class, () -> app);
            IdTokenVerifier verifier = token -> { throw FirebaseAuthException.denied(); };
            runner.withBean(IdTokenVerifier.class, () -> verifier).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(IdTokenVerifier.class).hasSingleBean(FirebaseAdminGateway.class);
                assertThat(context.getBean(IdTokenVerifier.class)).isSameAs(verifier);
            });
            FirebaseAdminGateway admin = mock(FirebaseAdminGateway.class);
            runner.withBean(FirebaseAdminGateway.class, () -> admin).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(IdTokenVerifier.class).hasSingleBean(FirebaseAdminGateway.class);
                assertThat(context.getBean(FirebaseAdminGateway.class)).isSameAs(admin);
            });
            assertThat(com.google.firebase.FirebaseApp.getInstance(app.getName())).isSameAs(app);
        } finally { app.delete(); }
    }
    @ParameterizedTest @ValueSource(strings = {"public", "security", "pg_catalog", "tenant_alpha", "álias", "alpha-beta", " alpha ", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void strictAliasRejectsReservedUnicodeAndOversized(String alias) {
        assertThatThrownBy(() -> new StrictTenantAliasValidator().validate(alias)).isInstanceOf(FirebaseAuthException.class);
    }
    @Test void aliasNormalizesAsciiUsingRootLocale() {
        var previous = java.util.Locale.getDefault();
        try { java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertThat(new StrictTenantAliasValidator().validate("ISTANBUL")).isEqualTo("istanbul");
        } finally { java.util.Locale.setDefault(previous); }
    }
    @Test void emulatorRequiresDemoProjectExplicitHostAndAllowedProfiles() {
        var properties = new FirebaseAuthProperties(true, "demo-gems", true, "X-Tenant-Alias", "gems-test", false, 0,
                new FirebaseAuthProperties.Emulator(true, "127.0.0.1:9099"));
        properties.validate("127.0.0.1:9099", new String[]{"test"});
        assertThatThrownBy(() -> properties.validate("127.0.0.1:9099", new String[]{"production"})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.validate(null, new String[]{"test"})).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void emulatorDockerServiceDnsIsAllowedOnlyWithConsistentDemoEnvironment() {
        var docker = new FirebaseAuthProperties(true, "demo-gems", true, "X-Tenant-Alias", "docker-test", false, 0,
                new FirebaseAuthProperties.Emulator(true, "auth-emulator:9099"));
        docker.validate("auth-emulator:9099", new String[]{"local"}); docker.validate("auth-emulator:9099", new String[]{"ci"});
        assertThatThrownBy(() -> docker.validate("auth-emulator:9099", new String[]{"production"})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> docker.validate("localhost:9099", new String[]{"local"})).isInstanceOf(IllegalArgumentException.class);
        var real = new FirebaseAuthProperties(true, "real-gems", true, "X-Tenant-Alias", "real-test", false, 0,
                new FirebaseAuthProperties.Emulator(true, "auth-emulator:9099"));
        assertThatThrownBy(() -> real.validate("auth-emulator:9099", new String[]{"local"})).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"http://auth-emulator:9099", "a..b:9099", "auth-emulator:0", "auth-emulator:65536", "user@auth-emulator:9099", "-invalid:9099"})
    void emulatorHostRejectsUrlMalformedDnsAndInvalidPorts(String host) {
        var properties = new FirebaseAuthProperties(true, "demo-gems", true, "X-Tenant-Alias", "test-app", false, 0,
                new FirebaseAuthProperties.Emulator(true, host));
        assertThatThrownBy(() -> properties.validate(host, new String[]{"local"})).isInstanceOf(IllegalArgumentException.class);
    }
}
