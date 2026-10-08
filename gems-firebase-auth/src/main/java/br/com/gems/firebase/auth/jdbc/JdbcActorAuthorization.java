package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.FirebaseAuthException;
import br.com.gems.firebase.auth.SecurityActorProvider;
import br.com.gems.security.authorization.AuthorizationCatalog;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Recalcula o ator no banco; serializa alterações de containers no tenant. */
final class JdbcActorAuthorization {
    private final SecurityJdbcSupport db;
    private final AuthorizationCatalog catalog;
    JdbcActorAuthorization(SecurityJdbcSupport db, AuthorizationCatalog catalog) { this.db = db; this.catalog = catalog; }
    Set<String> tenant(UUID tenant, Instant now) {
        String alias = db.jdbc.sql("SELECT cd_alias FROM security.tenant WHERE id_tenant=:tenant AND dt_inicio<=:now "
                + "AND (dt_fim IS NULL OR dt_fim>:now) FOR UPDATE").param("tenant", tenant)
                .param("now", SecurityJdbcSupport.time(now)).query(String.class).optional().orElseThrow(FirebaseAuthException::denied);
        var actor = db.actors.currentActor();
        if (actor.bootstrap()) { return catalog.tenantActions(); }
        if (actor.tenantAlias().filter(alias::equals).isEmpty()) { throw FirebaseAuthException.denied(); }
        return actions(actor, alias, now, "ACTION");
    }
    void central(String action, Instant now) {
        var actor = db.actors.currentActor();
        if (actor.bootstrap()) { return; }
        if (!catalog.globalActions().contains(action) || !actions(actor, null, now, "GLOBAL").contains(action)) {
            throw FirebaseAuthException.denied();
        }
    }
    private Set<String> actions(SecurityActorProvider.Actor actor, String alias, Instant now, String kind) {
        var identity = db.jdbc.sql("SELECT i.id_provedor_identidade,i.cd_emissor,i.cd_sujeito_externo "
                + "FROM security.usuario_identidade i WHERE i.id_usuario=:user AND i.dt_inicio<=:now "
                + "AND (i.dt_fim IS NULL OR i.dt_fim>:now)").param("user", actor.userId())
                .param("now", SecurityJdbcSupport.time(now)).query((rs, row) -> new Identity(rs.getObject(1, UUID.class),
                        rs.getString(2), rs.getString(3))).optional().orElseThrow(FirebaseAuthException::denied);
        var rows = db.jdbc.sql(JdbcAuthorizationResolver.EFFECTIVE_SQL).param("user", actor.userId())
                .param("provider", identity.provider()).param("issuer", identity.issuer()).param("uid", identity.uid())
                .param("alias", alias).param("now", SecurityJdbcSupport.time(now)).param("revision", 0)
                .param("tenantCatalog", catalog.tenantActions().isEmpty() ? Set.of("") : catalog.tenantActions())
                .param("globalCatalog", catalog.globalActions().isEmpty() ? Set.of("") : catalog.globalActions())
                .query((rs, row) -> new Row(rs.getString("kind"), rs.getString("value"))).list();
        if (rows.stream().noneMatch(r -> r.kind().equals("USER"))
                || (alias != null && rows.stream().noneMatch(r -> r.kind().equals("TENANT")))) { throw FirebaseAuthException.denied(); }
        return rows.stream().filter(r -> r.kind().equals(kind)).map(Row::value).collect(java.util.stream.Collectors.toSet());
    }
    private record Identity(UUID provider, String issuer, String uid) { }
    private record Row(String kind, String value) { }
}
