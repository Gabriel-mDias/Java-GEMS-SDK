package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.AuthorizationResolver;
import br.com.gems.firebase.auth.FirebaseAuthException;
import br.com.gems.firebase.auth.FirebasePrincipal;
import br.com.gems.firebase.auth.SecurityEventSink;
import br.com.gems.firebase.auth.TenantAliasValidator;
import br.com.gems.firebase.auth.VerifiedIdentity;
import br.com.gems.security.authorization.AuthorizationCatalog;
import br.com.gems.security.authorization.JwtAuthorizationContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Identidade UID-first e CTE de toda a cadeia vigente, sempre qualificada security. */
public final class JdbcAuthorizationResolver implements AuthorizationResolver {
    private final SecurityJdbcSupport db;
    private final AuthorizationCatalog catalog;
    private final TenantAliasValidator aliases;
    private final String project;
    private final long minimumRevision;
    public JdbcAuthorizationResolver(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events,
            AuthorizationCatalog catalog, TenantAliasValidator aliases, String project, long minimumRevision) {
        db = new SecurityJdbcSupport(jdbc, tx, events); this.catalog = catalog; this.aliases = aliases;
        this.project = project; this.minimumRevision = minimumRevision;
    }
    @Override public Resolution resolve(VerifiedIdentity identity, Optional<String> requestedAlias, Instant now) {
        try {
            if (!identity.projectId().equals(project)) { throw FirebaseAuthException.denied(); }
            return db.tx.execute(status -> resolveInTransaction(identity,
                    requestedAlias.map(aliases::validate), now));
        } catch (DataIntegrityViolationException failure) {
            refusal(now); throw FirebaseAuthException.denied();
        } catch (FirebaseAuthException | IllegalArgumentException failure) {
            refusal(now); throw FirebaseAuthException.denied();
        }
    }
    private void refusal(Instant now) {
        try { db.events.emit(new SecurityEventSink.Event(UUID.randomUUID(), "LOCAL_OR_TENANT_ACCESS_REFUSED", null, null, now,
                null, SecurityEventSink.TargetType.AUTHENTICATION, null, null, null, null, UUID.randomUUID())); }
        catch (RuntimeException unavailable) { /* recusa permanece fechada */ }
    }
    private Resolution resolveInTransaction(VerifiedIdentity identity, Optional<String> alias, Instant now) {
        UUID provider = db.jdbc.sql("SELECT id_provedor_identidade FROM security.provedor_identidade "
                + "WHERE cd_projeto=:project AND cd_emissor=:issuer AND dt_inicio<=:now "
                + "AND (dt_fim IS NULL OR dt_fim>:now)").param("project", project).param("issuer", identity.issuer())
                .param("now", SecurityJdbcSupport.time(now)).query(UUID.class).optional().orElseThrow(FirebaseAuthException::denied);
        List<IdentityRow> identities = findIdentity(provider, identity);
        if (identities.isEmpty()) {
            linkGoogle(provider, identity, now);
            identities = findIdentity(provider, identity);
        }
        if (identities.size() != 1) { throw FirebaseAuthException.denied(); }
        IdentityRow linked = identities.getFirst();
        if (!linked.openAt(now)) { throw FirebaseAuthException.denied(); }

        List<ActionRow> rows = db.jdbc.sql(EFFECTIVE_SQL).param("user", linked.user())
                .param("provider", provider).param("issuer", identity.issuer()).param("uid", identity.uid())
                .param("alias", alias.orElse(null)).param("now", SecurityJdbcSupport.time(now))
                .param("tenantCatalog", catalog.tenantActions().isEmpty() ? Set.of("") : catalog.tenantActions())
                .param("globalCatalog", catalog.globalActions().isEmpty() ? Set.of("") : catalog.globalActions())
                .param("revision", minimumRevision).query((rs, row) -> new ActionRow(rs.getString("kind"),
                        rs.getString("value"))).list();
        if (rows.stream().noneMatch(row -> row.kind().equals("USER"))) { throw FirebaseAuthException.denied(); }
        if (alias.isPresent() && rows.stream().noneMatch(row -> row.kind().equals("TENANT"))) {
            throw FirebaseAuthException.denied();
        }
        Set<String> global = values(rows, "GLOBAL");
        Set<String> tenant = values(rows, "ACTION");
        if (rows.stream().anyMatch(row -> row.kind().equals("DRIFT"))) {
            db.event("AUTHORIZATION_CATALOG_DRIFT", linked.user(), null, now);
        }
        global.retainAll(catalog.globalActions()); tenant.retainAll(catalog.tenantActions());
        FirebasePrincipal principal = db.jdbc.sql("SELECT nm_usuario,cd_email FROM security.usuario WHERE id_usuario=:user")
                .param("user", linked.user()).query((rs, row) -> new FirebasePrincipal(linked.user(), identity.uid(),
                        rs.getString(1), rs.getString(2))).single();
        var available = db.jdbc.sql("SELECT t.id_tenant,t.cd_alias,t.nm_tenant FROM security.tenant t JOIN security.usuario_tenant m ON m.id_tenant=t.id_tenant "
                + "WHERE m.id_usuario=:user AND m.dt_inicio<=:now AND (m.dt_fim IS NULL OR m.dt_fim>:now) "
                + "AND t.dt_inicio<=:now AND (t.dt_fim IS NULL OR t.dt_fim>:now) AND t.cd_provisionamento='READY' "
                + "AND t.nr_revisao_aplicada>=:revision ORDER BY t.cd_alias").param("user", linked.user())
                .param("now", SecurityJdbcSupport.time(now)).param("revision", minimumRevision)
                .query((rs, row) -> new br.com.gems.firebase.auth.AccountViews.Tenant(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3))).list();
        var selected = alias.map(value -> available.stream().filter(t -> t.alias().equals(value)).findFirst().orElseThrow(FirebaseAuthException::denied))
                .map(value -> new br.com.gems.firebase.auth.AccountViews.Context(value,
                        containers("perfil", value.id(), values(rows, "PROFILE"), now), containers("grupo", value.id(), values(rows, "GROUP"), now), tenant));
        return new Resolution(principal, new JwtAuthorizationContext(Set.of(), global, values(rows, "PROFILE"), tenant, alias),
                new br.com.gems.firebase.auth.AccountViews.Me(principal, available, global), selected);
    }
    private List<br.com.gems.firebase.auth.AccountViews.Container> containers(String table, UUID tenant, Set<String> codes, Instant now) {
        if (codes.isEmpty()) { return List.of(); }
        return db.jdbc.sql("SELECT id_" + table + ",cd_" + table + ",nm_" + table + " FROM security." + table
                + " WHERE id_tenant=:tenant AND cd_" + table + " IN (:codes) AND dt_inicio<=:now AND (dt_fim IS NULL OR dt_fim>:now) ORDER BY cd_" + table)
                .param("tenant", tenant).param("codes", codes).param("now", SecurityJdbcSupport.time(now)).query((rs, row) -> new br.com.gems.firebase.auth.AccountViews.Container(
                        rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3))).list();
    }
    private List<IdentityRow> findIdentity(UUID provider, VerifiedIdentity identity) {
        // Inclui histórico: identidade fechada impede absolutamente fallback por e-mail.
        return db.jdbc.sql("SELECT id_usuario,dt_inicio,dt_fim FROM security.usuario_identidade "
                + "WHERE id_provedor_identidade=:provider AND cd_emissor=:issuer AND cd_sujeito_externo=:uid")
                .param("provider", provider).param("issuer", identity.issuer()).param("uid", identity.uid())
                .query((rs, row) -> new IdentityRow(rs.getObject(1, UUID.class), instant(rs, 2), instant(rs, 3))).list();
    }
    private void linkGoogle(UUID provider, VerifiedIdentity identity, Instant now) {
        if (!"google.com".equals(identity.signInProvider()) || !identity.emailVerified() || identity.email() == null) {
            throw FirebaseAuthException.denied();
        }
        String email = identity.email().strip().toLowerCase(Locale.ROOT);
        db.jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
                .param("key", provider + ":" + identity.issuer() + ":" + identity.uid()).query().listOfRows();
        db.jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:email,0))").param("email", email).query().listOfRows();
        if (!findIdentity(provider, identity).isEmpty()) { return; }
        var eligible = db.jdbc.sql("SELECT id_usuario FROM security.usuario WHERE cd_email=:email "
                + "AND cd_elegibilidade='GOOGLE_PENDING' AND dt_inicio<=:now AND (dt_fim IS NULL OR dt_fim>:now) FOR UPDATE")
                .param("email", email).param("now", SecurityJdbcSupport.time(now)).query(UUID.class).list();
        if (eligible.size() != 1 || !findIdentity(provider, identity).isEmpty()) { throw FirebaseAuthException.denied(); }
        UUID user = eligible.getFirst();
        int changed = db.jdbc.sql("UPDATE security.usuario SET cd_elegibilidade='BOUND',dt_alteracao=:now "
                + "WHERE id_usuario=:user AND cd_elegibilidade='GOOGLE_PENDING'").param("user", user)
                .param("now", SecurityJdbcSupport.time(now)).update();
        if (changed != 1) { throw FirebaseAuthException.denied(); }
        db.jdbc.sql("INSERT INTO security.usuario_identidade(id_usuario_identidade,id_usuario,id_provedor_identidade,"
                + "cd_emissor,cd_sujeito_externo,cd_email_identidade,dt_inicio) VALUES(:id,:user,:provider,:issuer,:uid,:email,:now)")
                .param("id", UUID.randomUUID()).param("user", user).param("provider", provider).param("issuer", identity.issuer())
                .param("uid", identity.uid()).param("email", email).param("now", SecurityJdbcSupport.time(now)).update();
        db.event("GOOGLE_IDENTITY_LINKED", user, null, now);
    }
    private static Instant instant(ResultSet rs, int column) throws SQLException {
        var value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private static Set<String> values(List<ActionRow> rows, String kind) {
        var values = new HashSet<String>();
        rows.stream().filter(row -> row.kind().equals(kind)).map(ActionRow::value).forEach(values::add);
        return values;
    }
    private record IdentityRow(UUID user, Instant start, Instant end) {
        boolean openAt(Instant t) { return !start.isAfter(t) && (end == null || end.isAfter(t)); }
    }
    private record ActionRow(String kind, String value) { }

    static final String EFFECTIVE_SQL = """
            WITH live_user AS (
              SELECT u.* FROM security.usuario u
              JOIN security.usuario_identidade i ON i.id_usuario=u.id_usuario
              JOIN security.provedor_identidade p ON p.id_provedor_identidade=i.id_provedor_identidade
              WHERE u.id_usuario=:user AND u.cd_elegibilidade='BOUND'
              AND i.id_provedor_identidade=:provider AND i.cd_emissor=:issuer AND i.cd_sujeito_externo=:uid
              AND u.dt_inicio<=:now AND (u.dt_fim IS NULL OR u.dt_fim>:now)
              AND i.dt_inicio<=:now AND (i.dt_fim IS NULL OR i.dt_fim>:now)
              AND p.dt_inicio<=:now AND (p.dt_fim IS NULL OR p.dt_fim>:now)
            ), membership AS (
              SELECT m.* FROM security.usuario_tenant m JOIN live_user u ON u.id_usuario=m.id_usuario
              JOIN security.tenant t ON t.id_tenant=m.id_tenant
              WHERE t.cd_alias=:alias AND t.cd_provisionamento='READY' AND t.nr_revisao_aplicada>=:revision
              AND m.dt_inicio<=:now AND (m.dt_fim IS NULL OR m.dt_fim>:now)
              AND t.dt_inicio<=:now AND (t.dt_fim IS NULL OR t.dt_fim>:now)
            ), groups AS (
              SELECT g.* FROM security.grupo g
              JOIN security.grupo_usuario gu ON gu.id_tenant=g.id_tenant AND gu.id_grupo=g.id_grupo
              JOIN membership m ON m.id_tenant=gu.id_tenant AND m.id_usuario_tenant=gu.id_usuario_tenant
              WHERE g.dt_inicio<=:now AND (g.dt_fim IS NULL OR g.dt_fim>:now)
              AND gu.dt_inicio<=:now AND (gu.dt_fim IS NULL OR gu.dt_fim>:now)
            ), profile_ids AS (
              SELECT up.id_tenant,up.id_perfil FROM security.usuario_tenant_perfil up
              JOIN membership m ON m.id_tenant=up.id_tenant AND m.id_usuario_tenant=up.id_usuario_tenant
              WHERE up.dt_inicio<=:now AND (up.dt_fim IS NULL OR up.dt_fim>:now)
              UNION SELECT gp.id_tenant,gp.id_perfil FROM security.grupo_perfil gp
              JOIN groups g ON g.id_tenant=gp.id_tenant AND g.id_grupo=gp.id_grupo
              WHERE gp.dt_inicio<=:now AND (gp.dt_fim IS NULL OR gp.dt_fim>:now)
            ), profiles AS (
              SELECT p.* FROM security.perfil p JOIN profile_ids ids ON ids.id_tenant=p.id_tenant AND ids.id_perfil=p.id_perfil
              WHERE p.dt_inicio<=:now AND (p.dt_fim IS NULL OR p.dt_fim>:now)
            ), role_ids AS (
              SELECT ur.id_role FROM security.usuario_tenant_role ur
              JOIN membership m ON m.id_tenant=ur.id_tenant AND m.id_usuario_tenant=ur.id_usuario_tenant
              WHERE ur.dt_inicio<=:now AND (ur.dt_fim IS NULL OR ur.dt_fim>:now)
              UNION SELECT pr.id_role FROM security.perfil_role pr
              JOIN profiles p ON p.id_tenant=pr.id_tenant AND p.id_perfil=pr.id_perfil
              WHERE pr.dt_inicio<=:now AND (pr.dt_fim IS NULL OR pr.dt_fim>:now)
              UNION SELECT gr.id_role FROM security.grupo_role gr
              JOIN groups g ON g.id_tenant=gr.id_tenant AND g.id_grupo=gr.id_grupo
              WHERE gr.dt_inicio<=:now AND (gr.dt_fim IS NULL OR gr.dt_fim>:now)
            )
            SELECT 'USER' AS kind, CAST(id_usuario AS text) AS value FROM live_user
            UNION SELECT 'TENANT', CAST(id_tenant AS text) FROM membership
            UNION SELECT 'PROFILE',cd_perfil FROM profiles
            UNION SELECT 'GROUP',cd_grupo FROM groups
            UNION SELECT 'ACTION',r.cd_acao FROM security.role_seguranca r JOIN role_ids ids ON ids.id_role=r.id_role
              WHERE r.cd_escopo='TENANT' AND r.cd_acao IN (:tenantCatalog) AND r.dt_inicio<=:now AND (r.dt_fim IS NULL OR r.dt_fim>:now)
            UNION SELECT 'GLOBAL',r.cd_acao FROM security.role_seguranca r
              JOIN security.usuario_role_global ur ON ur.id_role=r.id_role JOIN live_user u ON u.id_usuario=ur.id_usuario
              WHERE r.cd_escopo='GLOBAL' AND r.cd_acao IN (:globalCatalog) AND r.dt_inicio<=:now AND (r.dt_fim IS NULL OR r.dt_fim>:now)
              AND ur.dt_inicio<=:now AND (ur.dt_fim IS NULL OR ur.dt_fim>:now)
            UNION SELECT 'DRIFT',r.cd_acao FROM security.role_seguranca r JOIN role_ids ids ON ids.id_role=r.id_role
              WHERE r.cd_acao NOT IN (:tenantCatalog) AND r.dt_inicio<=:now AND (r.dt_fim IS NULL OR r.dt_fim>:now)
            UNION SELECT 'DRIFT',r.cd_acao FROM security.role_seguranca r
              JOIN security.usuario_role_global ur ON ur.id_role=r.id_role JOIN live_user u ON u.id_usuario=ur.id_usuario
              WHERE r.cd_acao NOT IN (:globalCatalog) AND r.dt_inicio<=:now AND (r.dt_fim IS NULL OR r.dt_fim>:now)
              AND ur.dt_inicio<=:now AND (ur.dt_fim IS NULL OR ur.dt_fim>:now)
            """;
}
