package br.com.gems.firebase.auth;

import org.springframework.security.core.context.SecurityContextHolder;

/** Usa somente Authentication produzida pela verificação Firebase e resolução local. */
public final class SpringSecurityActorProvider implements SecurityActorProvider {
    @Override public Actor currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof FirebaseAuthenticationToken token) || !token.isAuthenticated()) {
            throw FirebaseAuthException.denied();
        }
        return new Actor(token.getPrincipal().userId(), false, token.authorizationContext().tenantAlias());
    }
}
