package br.com.gems.firebase.auth.config;

import br.com.gems.firebase.auth.AuthorizationResolver;
import br.com.gems.firebase.auth.FirebaseAdminGateway;
import br.com.gems.firebase.auth.FirebaseAuthProperties;
import br.com.gems.firebase.auth.FirebaseAuthenticationFilter;
import br.com.gems.firebase.auth.IdTokenVerifier;
import br.com.gems.firebase.auth.SecurityEventSink;
import br.com.gems.firebase.auth.SecurityEventPublisher;
import br.com.gems.firebase.auth.StrictTenantAliasValidator;
import br.com.gems.firebase.auth.TenantAliasValidator;
import br.com.gems.firebase.auth.internal.FirebaseAdminAdapter;
import br.com.gems.firebase.auth.internal.OwnedFirebaseApp;
import com.google.firebase.FirebaseApp;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;

/** Opt-in; não cria chain nem registra o filtro no container servlet. */
@AutoConfiguration
@ConditionalOnProperty(prefix = "gems.firebase.auth", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FirebaseAuthProperties.class)
public class FirebaseAuthAutoConfiguration {
    @Bean public FirebaseCatalogValidated firebaseCatalogValidated(br.com.gems.security.authorization.AuthorizationCatalog catalog) {
        return new FirebaseCatalogValidated();
    }
    /** Marcador: opt-in nunca dispensa o catálogo de código do consumidor. */
    public static final class FirebaseCatalogValidated { }
    /** Falha de configuração deve preceder uso de qualquer adapter. */
    @Bean public FirebaseConfigurationValidated firebaseConfigurationValidated(FirebaseAuthProperties properties,
            Environment environment) {
        properties.validate(System.getenv("FIREBASE_AUTH_EMULATOR_HOST"), environment.getActiveProfiles());
        return new FirebaseConfigurationValidated();
    }
    /** Marcador interno de validação do ambiente. */
    public static final class FirebaseConfigurationValidated { }
    @Bean @ConditionalOnMissingBean public Clock firebaseClock() { return Clock.systemUTC(); }
    @Bean @ConditionalOnMissingBean public TenantAliasValidator firebaseTenantAliasValidator() {
        return new StrictTenantAliasValidator();
    }
    @Bean @ConditionalOnMissingBean(SecurityEventSink.class)
    public SecurityEventPublisher firebaseSecurityEventSink() { return new br.com.gems.firebase.auth.LoggingSecurityEventPublisher(); }

    /** Um override de ambas as portas dispensa FirebaseApp/ADC. */
    @Configuration(proxyBeanMethods = false)
    @Conditional(NeedsDefaultAdmin.class)
    static class DefaultAdmin {
        @Bean(destroyMethod = "close") @ConditionalOnMissingBean(FirebaseApp.class)
        OwnedFirebaseApp ownedFirebaseApp(FirebaseAuthProperties properties, FirebaseConfigurationValidated validated) {
            return new OwnedFirebaseApp(properties);
        }
        @Bean(destroyMethod = "") @ConditionalOnMissingBean
        FirebaseApp firebaseApp(OwnedFirebaseApp owner) { return owner.app(); }
        @Bean @ConditionalOnMissingBean
        FirebaseAdminAdapter firebaseAdminAdapter(FirebaseApp app, FirebaseAuthProperties properties,
                FirebaseConfigurationValidated validated) {
            if (!properties.projectId().equals(app.getOptions().getProjectId())) {
                throw new IllegalArgumentException("Projeto Firebase inconsistente");
            }
            return new FirebaseAdminAdapter(app, properties.projectId(), properties.checkRevoked(), properties.emulator().enabled());
        }
        @Bean @ConditionalOnMissingBean(IdTokenVerifier.class)
        IdTokenVerifier firebaseIdTokenVerifier(FirebaseAdminAdapter adapter) { return adapter::verify; }
        @Bean @ConditionalOnMissingBean(FirebaseAdminGateway.class)
        FirebaseAdminGateway firebaseAdminGateway(FirebaseAdminAdapter adapter) { return new Gateway(adapter); }
    }
    static final class NeedsDefaultAdmin extends AnyNestedCondition {
        NeedsDefaultAdmin() { super(ConfigurationPhase.REGISTER_BEAN); }
        @ConditionalOnMissingBean(IdTokenVerifier.class) static class VerifierMissing { }
        @ConditionalOnMissingBean(FirebaseAdminGateway.class) static class AdminMissing { }
    }
    private record Gateway(FirebaseAdminAdapter adapter) implements FirebaseAdminGateway {
        @Override public User getUser(String uid) { return adapter.getUser(uid); }
        @Override public User createUser(String uid, String email, String displayName) { return adapter.createUser(uid, email, displayName); }
        @Override public void setDisabled(String uid, boolean disabled) { adapter.setDisabled(uid, disabled); }
        @Override public void revokeRefreshTokens(String uid) { adapter.revokeRefreshTokens(uid); }
        @Override public String passwordResetLink(String email) { return adapter.passwordResetLink(email); }
    }
}
