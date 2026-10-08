package br.com.gems.firebase.auth.config;

import br.com.gems.firebase.auth.AuthorizationResolver;
import br.com.gems.firebase.auth.FirebaseAuthProperties;
import br.com.gems.firebase.auth.FirebaseAuthenticationFilter;
import br.com.gems.firebase.auth.IdTokenVerifier;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/** Filtro disponibilizado depois dos resolvers; somente a chain do consumidor o registra. */
@AutoConfiguration(after = {FirebaseAuthAutoConfiguration.class, FirebaseJdbcAutoConfiguration.class})
@ConditionalOnProperty(prefix = "gems.firebase.auth", name = "enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean({IdTokenVerifier.class, AuthorizationResolver.class})
public class FirebaseServletAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    public FirebaseAuthenticationFilter firebaseAuthenticationFilter(IdTokenVerifier verifier, AuthorizationResolver resolver,
            Clock clock, FirebaseAuthProperties properties, br.com.gems.firebase.auth.SecurityEventSink events) {
        return new FirebaseAuthenticationFilter(verifier, resolver, clock, properties.tenantHeader(), events);
    }
    @Bean @ConditionalOnMissingBean(name = "firebaseFilterRegistration")
    public FilterRegistrationBean<FirebaseAuthenticationFilter> firebaseFilterRegistration(FirebaseAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
    }
}
