package br.com.gems.smoke;

import br.com.gems.auditing.AuditActorProvider;
import br.com.gems.auditing.AuditingAutoConfiguration;
import br.com.gems.keycloak.admin.KeycloakAdminGateway;
import br.com.gems.keycloak.admin.KeycloakAdminProperties;
import br.com.gems.security.authorization.AuthorizationCatalog;
import java.time.Duration;
import java.util.Set;

public final class Api310Smoke {
    private Api310Smoke() {}

    public static void main(String[] args) {
        AuditActorProvider actors = () -> null;
        new AuditingAutoConfiguration().auditingHibernateCustomizer(actors, null);
        KeycloakAdminGateway gateway = null;
        KeycloakAdminGateway.User user = new KeycloakAdminGateway.User("id", "name", "user", "mail", true);
        KeycloakAdminProperties properties = new KeycloakAdminProperties(
                "https://keycloak", "realm", "client", "secret", Duration.ofSeconds(1), Duration.ofSeconds(1));
        AuthorizationCatalog catalog = new AuthorizationCatalog(Set.of("READ_USER"), Set.of("READ_ORG"));
        if (gateway != null && user.enabled() && properties.realm().isBlank() && catalog.globalActions().isEmpty()) {
            throw new AssertionError();
        }
    }
}
