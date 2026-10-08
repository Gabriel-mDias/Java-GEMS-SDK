package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.*;
import br.com.gems.security.authorization.AuthorizationCatalog;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Seed técnico central explícito, separado da delegação normal e sem capacidade vinda de payload. */
public final class JdbcTenantAdministratorBootstrap implements TenantAdministratorBootstrap {
    private final SecurityJdbcSupport db;
    private final JdbcActorAuthorization actors;
    private final AuthorizationCatalog catalog;
    private final DelegationPolicy delegation;
    private final Clock clock;
    private final TenantAliasValidator aliases;
    public JdbcTenantAdministratorBootstrap(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events,
            AuthorizationCatalog catalog, DelegationPolicy delegation, Clock clock, TenantAliasValidator aliases, SecurityActorProvider actorProvider) {
        db = new SecurityJdbcSupport(jdbc, tx, events, actorProvider); actors = new JdbcActorAuthorization(db, catalog);
        this.catalog = catalog; this.delegation = delegation; this.clock = clock; this.aliases = aliases;
    }
    @Override public UUID initialize(UUID tenant, UUID user, Set<String> requested) {
        Set<String> actions = Set.copyOf(requested);
        return db.tx.execute(status -> {
            var now = clock.instant(); actors.central("CRIAR_ADMINISTRADOR_TENANT", now);
            if (!catalog.tenantActions().containsAll(actions) || !delegation.delegableTenantActions().containsAll(actions)) { throw FirebaseAuthException.denied(); }
            db.jdbc.sql("SELECT u.id_usuario FROM security.usuario u JOIN security.usuario_identidade i ON i.id_usuario=u.id_usuario "
                    + "JOIN security.provedor_identidade p ON p.id_provedor_identidade=i.id_provedor_identidade "
                    + "WHERE u.id_usuario=:user AND u.cd_elegibilidade='BOUND' AND u.dt_inicio<=:now AND (u.dt_fim IS NULL OR u.dt_fim>:now) "
                    + "AND i.dt_inicio<=:now AND (i.dt_fim IS NULL OR i.dt_fim>:now) AND p.dt_inicio<=:now AND (p.dt_fim IS NULL OR p.dt_fim>:now)")
                    .param("user", user).param("now", SecurityJdbcSupport.time(now)).query(UUID.class).optional().orElseThrow(FirebaseAuthException::denied);
            SecurityActorProvider seed = SecurityActorProvider.trustedBootstrap(db.actors.currentActor().userId());
            var tenants = new JdbcTenantService(db.jdbc, db.tx, db.events, clock, aliases, catalog, seed);
            var grants = new JdbcGrantService(db.jdbc, db.tx, db.events, catalog, delegation, clock, seed);
            UUID membership = tenants.addMember(tenant, user);
            actions.forEach(action -> grants.grant(GrantAdministration.Target.MEMBER, tenant, membership, action));
            db.event("TENANT_ADMIN_BOOTSTRAPPED", user, tenant, now, SecurityEventSink.TargetType.MEMBERSHIP, membership, user, tenant, null);
            return membership;
        });
    }
}
