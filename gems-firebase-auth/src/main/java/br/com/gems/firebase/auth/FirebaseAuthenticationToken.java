package br.com.gems.firebase.auth;

import br.com.gems.security.authorization.AuthorizationContextAware;
import br.com.gems.security.authorization.JwtAuthorizationContext;
import java.util.stream.Stream;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Authentication com autoridades concretas derivadas da autorização SQL. */
public final class FirebaseAuthenticationToken extends AbstractAuthenticationToken implements AuthorizationContextAware {
    private final FirebasePrincipal principal;
    private final JwtAuthorizationContext context;
    private final AccountViews.Me me;
    private final java.util.Optional<AccountViews.Context> tenantContext;
    public FirebaseAuthenticationToken(AuthorizationResolver.Resolution resolution) {
        super(Stream.concat(resolution.context().globalActions().stream(), resolution.context().tenantActions().stream())
                .distinct().map(action -> new SimpleGrantedAuthority("ROLE_" + action)).toList());
        principal = resolution.principal();
        context = resolution.context();
        me = resolution.me(); tenantContext = resolution.tenantContext();
        super.setAuthenticated(true);
    }
    @Override public Object getCredentials() { return null; }
    @Override public FirebasePrincipal getPrincipal() { return principal; }
    @Override public String getName() { return principal.userId().toString(); }
    @Override public JwtAuthorizationContext authorizationContext() { return context; }
    public AccountViews.Me me() { return me; }
    public java.util.Optional<AccountViews.Context> tenantContext() { return tenantContext; }
    @Override public void setAuthenticated(boolean authenticated) {
        if (authenticated) { throw new IllegalArgumentException("Use uma resolução comprovada"); }
        super.setAuthenticated(false);
    }
}
