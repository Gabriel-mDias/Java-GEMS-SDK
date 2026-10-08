package br.com.gems.firebase.auth;

import br.com.gems.security.authorization.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Consumer MVC real usa a lista publicada completa, sua própria chain e os endpoints canônicos. */
class FirebaseMvcIntegrationTest {
    static final AuthorizationCatalog CATALOG = new AuthorizationCatalog(Set.of("CONSULTAR_PROPRIA_CONTA"), Set.of("CONSULTAR_PROPRIO_CONTEXTO_TENANT"));
    static Class<?>[] imports() {
        try (var input = FirebaseMvcIntegrationTest.class.getResourceAsStream("/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            var names = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().filter(s -> !s.isBlank()).toList();
            assertThat(names).doesNotHaveDuplicates();
            var result = new java.util.ArrayList<Class<?>>(); for (String name : names) { result.add(Class.forName(name)); }
            return result.toArray(Class<?>[]::new);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    WebApplicationContextRunner base() {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(imports()))
                .withUserConfiguration(Web.class)
                .withPropertyValues("gems.firebase.auth.enabled=true", "gems.firebase.auth.jdbc-enabled=true", "gems.firebase.auth.project-id=stub-gems")
                .withBean(IdTokenVerifier.class, () -> value -> {
                    if (!value.equals("valid")) { throw new FirebaseAuthException(FirebaseAuthException.Reason.INVALID_TOKEN); }
                    return new VerifiedIdentity("stub-gems", "https://securetoken.google.com/stub-gems", "uid", "person@example.test", true, "google.com", "Person", Instant.now());
                }).withBean(FirebaseAdminGateway.class, () -> mock(FirebaseAdminGateway.class))
                .withBean(AuthorizationResolver.class, () -> (identity, alias, now) -> {
                    if (alias.isPresent() && !alias.get().equals("alpha")) { throw FirebaseAuthException.denied(); }
                    var user = new FirebasePrincipal(new UUID(0, 1), "uid", "Person", "person@example.test");
                    var tenant = new AccountViews.Tenant(new UUID(0, 2), "alpha", "Alpha");
                    Set<String> actions = alias.isPresent() ? CATALOG.tenantActions() : Set.of();
                    var context = new JwtAuthorizationContext(Set.of(), CATALOG.globalActions(), Set.of(), actions, alias);
                    return new AuthorizationResolver.Resolution(user, context, new AccountViews.Me(user, List.of(tenant), CATALOG.globalActions()),
                            alias.map(a -> new AccountViews.Context(tenant, List.of(), List.of(), actions)));
                });
    }
    @Test void missingCatalogFailsEvenWithCustomResolverAndNoDataSource() {
        base().withBean(AccountController.class).run(context -> assertThat(context).hasFailed());
    }
    @Test void unclassifiedEndpointFailsWithEntireAutoConfigurationList() {
        base().withBean(AuthorizationCatalog.class, () -> CATALOG).withBean(BrokenController.class)
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void canonicalMeAndContextUseScannerInterceptorAndExactlyOneFilterInConsumerChain() {
        base().withBean(AuthorizationCatalog.class, () -> CATALOG).withBean(AccountController.class).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(javax.sql.DataSource.class);
            assertThat(context).hasSingleBean(TenantAuthorizationInterceptor.class);
            var registration = context.getBean(org.springframework.boot.web.servlet.FilterRegistrationBean.class);
            assertThat(registration.isEnabled()).isFalse();
            var chain = context.getBean(org.springframework.security.web.FilterChainProxy.class);
            assertThat(chain.getFilterChains()).hasSize(1);
            assertThat(chain.getFilterChains().getFirst().getFilters().stream().filter(FirebaseAuthenticationFilter.class::isInstance).count()).isEqualTo(1);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(chain).build();
            try {
                var me = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer valid")).andReturn().getResponse();
                assertThat(me.getStatus()).isEqualTo(200); assertThat(me.getContentAsString()).isEqualTo("Person:Alpha");
                var selected = mvc.perform(get("/api/auth/context").header("Authorization", "Bearer valid").header("X-Tenant-Alias", "alpha")).andReturn().getResponse();
                assertThat(selected.getStatus()).isEqualTo(200); assertThat(selected.getContentAsString()).isEqualTo("Alpha");
                assertThat(mvc.perform(get("/api/auth/context").header("Authorization", "Bearer valid")).andReturn().getResponse().getStatus()).isEqualTo(403);
                assertThat(mvc.perform(get("/api/auth/context").header("Authorization", "Bearer valid").header("X-Tenant-Alias", "beta")).andReturn().getResponse().getStatus()).isEqualTo(403);
                assertThat(mvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid")).andReturn().getResponse().getStatus()).isEqualTo(401);
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            } catch (Exception failure) { throw new AssertionError(failure); }
        });
    }
    @Configuration(proxyBeanMethods = false) @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Web {
        @Bean SecurityFilterChain consumerChain(HttpSecurity http, FirebaseAuthenticationFilter filter) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, failure) -> response.setStatus(401)))
                    .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
        }
    }
    @RestController static class AccountController {
        @GetMapping("/api/auth/me") @GlobalEndpoint @PreAuthorize("hasRole('CONSULTAR_PROPRIA_CONTA')")
        public String me() {
            var token = (FirebaseAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
            return token.me().user().name() + ":" + token.me().tenants().getFirst().name();
        }
        @GetMapping("/api/auth/context") @TenantEndpoint @PreAuthorize("hasRole('CONSULTAR_PROPRIO_CONTEXTO_TENANT')")
        public String context() {
            return ((FirebaseAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).tenantContext().orElseThrow().tenant().name();
        }
    }
    @RestController static class BrokenController { @GetMapping("/broken") public String broken() { return "broken"; } }
}
