package br.com.gems.firebase.auth;

import br.com.gems.security.authorization.JwtAuthorizationContext;
import java.time.Instant;
import java.util.Optional;

/** Resolve identidade local e contexto vigente sem exigir DataSource no contrato. */
@FunctionalInterface
public interface AuthorizationResolver {
    /** Snapshot único; header é solicitação, não prova. */
    Resolution resolve(VerifiedIdentity identity, Optional<String> requestedAlias, Instant now);
    /** Resultado imutável da resolução. */
    record Resolution(FirebasePrincipal principal, JwtAuthorizationContext context, AccountViews.Me me,
            Optional<AccountViews.Context> tenantContext) {
        public Resolution(FirebasePrincipal principal, JwtAuthorizationContext context) {
            this(principal, context, new AccountViews.Me(principal, java.util.List.of(), context.globalActions()), Optional.empty());
        }
    }
}
