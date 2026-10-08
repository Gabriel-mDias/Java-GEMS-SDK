package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.AdministrationViews.*;
import br.com.gems.firebase.auth.SecurityActorProvider;
import br.com.gems.firebase.auth.SecurityAdministrationReader;
import br.com.gems.firebase.auth.SecurityEventSink;
import br.com.gems.security.authorization.AuthorizationCatalog;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Consultas com scope comprovado e paginação por UUID; identificadores SQL são constantes internas. */
public final class JdbcSecurityAdministrationReader implements SecurityAdministrationReader {
    private final SecurityJdbcSupport db;
    private final JdbcActorAuthorization actors;
    private final Clock clock;
    public JdbcSecurityAdministrationReader(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events,
            AuthorizationCatalog catalog, Clock clock, SecurityActorProvider actorProvider) {
        db = new SecurityJdbcSupport(jdbc, tx, events, actorProvider); actors = new JdbcActorAuthorization(db, catalog); this.clock = clock;
    }
    @Override public Optional<User> findUser(UUID id) { return one("usuario", "id_usuario", null, id, JdbcSecurityAdministrationReader::user, "CONSULTAR_USUARIO"); }
    @Override public Page<User> listUsers(Query q) { return page("usuario", "id_usuario", null, q, JdbcSecurityAdministrationReader::user, User::id, "CONSULTAR_USUARIO"); }
    @Override public Optional<Tenant> findTenant(UUID id) { return one("tenant", "id_tenant", null, id, JdbcSecurityAdministrationReader::tenant, "CONSULTAR_TENANT"); }
    @Override public Page<Tenant> listTenants(Query q) { return page("tenant", "id_tenant", null, q, JdbcSecurityAdministrationReader::tenant, Tenant::id, "CONSULTAR_TENANT"); }
    @Override public Optional<Profile> findProfile(UUID tenant, UUID id) { return one("perfil", "id_perfil", tenant, id, JdbcSecurityAdministrationReader::profile, null); }
    @Override public Page<Profile> listProfiles(UUID tenant, Query q) { return page("perfil", "id_perfil", tenant, q, JdbcSecurityAdministrationReader::profile, Profile::id, null); }
    @Override public Optional<Group> findGroup(UUID tenant, UUID id) { return one("grupo", "id_grupo", tenant, id, JdbcSecurityAdministrationReader::group, null); }
    @Override public Page<Group> listGroups(UUID tenant, Query q) { return page("grupo", "id_grupo", tenant, q, JdbcSecurityAdministrationReader::group, Group::id, null); }
    @Override public Optional<Membership> findMembership(UUID tenant, UUID id) { return one("usuario_tenant", "id_usuario_tenant", tenant, id, JdbcSecurityAdministrationReader::membership, null); }
    @Override public Page<Membership> listMemberships(UUID tenant, Query q) { return page("usuario_tenant", "id_usuario_tenant", tenant, q, JdbcSecurityAdministrationReader::membership, Membership::id, null); }
    private <T> Optional<T> one(String table, String key, UUID tenant, UUID id, RowMapper<T> mapper, String central) {
        return db.tx.execute(status -> {
            scope(tenant, central);
            return db.jdbc.sql("SELECT * FROM security." + table + " WHERE " + key + "=:id" + (tenant == null ? "" : " AND id_tenant=:tenant"))
                    .param("id", id).param("tenant", tenant).query(mapper).optional();
        });
    }
    private <T> Page<T> page(String table, String key, UUID tenant, Query q, RowMapper<T> mapper, Function<T, UUID> id, String central) {
        return db.tx.execute(status -> {
            scope(tenant, central);
            var rows = db.jdbc.sql("SELECT * FROM security." + table + " WHERE (:first OR " + key + ">:after)"
                    + (tenant == null ? "" : " AND id_tenant=:tenant")
                    + " AND (:history OR (dt_inicio<=:now AND (dt_fim IS NULL OR dt_fim>:now))) ORDER BY " + key + " LIMIT :limit")
                    .param("first", q.afterId() == null).param("after", q.afterId() == null ? new UUID(0, 0) : q.afterId())
                    .param("tenant", tenant).param("history", q.includeHistory()).param("now", SecurityJdbcSupport.time(clock.instant()))
                    .param("limit", q.limit() + 1).query(mapper).list();
            return slice(rows, q.limit(), id);
        });
    }
    private void scope(UUID tenant, String central) { if (tenant == null) { actors.central(central, clock.instant()); } else { actors.tenant(tenant, clock.instant()); } }
    @Override public Optional<Grant> findGrant(UUID tenant, UUID id) { return grants(tenant, null, id, new Query(null, 1, true)).items().stream().findFirst(); }
    @Override public Page<Grant> listGrants(UUID tenant, Query q) { return grants(tenant, null, null, q); }
    @Override public Optional<Grant> findGlobalGrant(UUID user, UUID id) { return grants(null, user, id, new Query(null, 1, true)).items().stream().findFirst(); }
    @Override public Page<Grant> listGlobalGrants(UUID user, Query q) { return grants(null, user, null, q); }
    private Page<Grant> grants(UUID tenant, UUID user, UUID selected, Query q) {
        if (tenant == null && user == null) { throw new IllegalArgumentException("Scope obrigatório"); }
        return db.tx.execute(status -> {
            scope(tenant, "CONSULTAR_ACAO_GLOBAL");
            var rows = db.jdbc.sql("SELECT * FROM (" + (tenant == null ? GLOBAL_GRANTS : TENANT_GRANTS) + ") g "
                    + "WHERE (:first OR id>:after) AND (:all OR id=:selected) "
                    + "AND (:history OR (dt_inicio<=:now AND (dt_fim IS NULL OR dt_fim>:now))) ORDER BY id LIMIT :limit")
                    .param("tenant", tenant).param("user", user).param("first", q.afterId() == null)
                    .param("after", q.afterId() == null ? new UUID(0, 0) : q.afterId()).param("all", selected == null)
                    .param("selected", selected == null ? new UUID(0, 0) : selected).param("history", q.includeHistory())
                    .param("now", SecurityJdbcSupport.time(clock.instant())).param("limit", q.limit() + 1)
                    .query((rs, row) -> new Grant(rs.getObject("id", UUID.class), tenant, SecurityEventSink.TargetType.valueOf(rs.getString("kind")),
                            rs.getObject("source", UUID.class), rs.getObject("related", UUID.class), rs.getString("action"), instant(rs, "dt_inicio"), instant(rs, "dt_fim"))).list();
            return slice(rows, q.limit(), Grant::id);
        });
    }
    private static <T> Page<T> slice(List<T> rows, int limit, Function<T, UUID> id) {
        return rows.size() > limit ? new Page<>(rows.subList(0, limit), id.apply(rows.get(limit - 1))) : new Page<>(rows, null);
    }
    private static Instant instant(ResultSet rs, String key) throws SQLException { var value = rs.getTimestamp(key); return value == null ? null : value.toInstant(); }
    private static User user(ResultSet rs, int row) throws SQLException { return new User(rs.getObject("id_usuario", UUID.class), rs.getString("nm_usuario"), rs.getString("cd_email"), Eligibility.valueOf(rs.getString("cd_elegibilidade")), instant(rs, "dt_inicio"), instant(rs, "dt_fim")); }
    private static Tenant tenant(ResultSet rs, int row) throws SQLException { return new Tenant(rs.getObject("id_tenant", UUID.class), rs.getString("cd_alias"), rs.getString("nm_tenant"), Provisioning.valueOf(rs.getString("cd_provisionamento")), rs.getLong("nr_revisao_aplicada"), instant(rs, "dt_inicio"), instant(rs, "dt_fim")); }
    private static Profile profile(ResultSet rs, int row) throws SQLException { return new Profile(rs.getObject("id_perfil", UUID.class), rs.getObject("id_tenant", UUID.class), rs.getString("cd_perfil"), rs.getString("nm_perfil"), rs.getString("ds_perfil"), instant(rs, "dt_inicio"), instant(rs, "dt_fim")); }
    private static Group group(ResultSet rs, int row) throws SQLException { return new Group(rs.getObject("id_grupo", UUID.class), rs.getObject("id_tenant", UUID.class), rs.getString("cd_grupo"), rs.getString("nm_grupo"), rs.getString("ds_grupo"), instant(rs, "dt_inicio"), instant(rs, "dt_fim")); }
    private static Membership membership(ResultSet rs, int row) throws SQLException { return new Membership(rs.getObject("id_usuario_tenant", UUID.class), rs.getObject("id_tenant", UUID.class), rs.getObject("id_usuario", UUID.class), instant(rs, "dt_inicio"), instant(rs, "dt_fim")); }
    private static String actionGrant(String table, String owner, String kind) {
        return "SELECT g.id_" + table + " AS id,'" + kind + "' AS kind,g." + owner + " AS source,g.id_role AS related,r.cd_acao AS action,g.dt_inicio,g.dt_fim "
                + "FROM security." + table + " g JOIN security.role_seguranca r ON r.id_role=g.id_role WHERE g.id_tenant=:tenant";
    }
    private static String linkGrant(String table, String left, String right, String kind) {
        return "SELECT id_" + table + " AS id,'" + kind + "' AS kind," + left + " AS source," + right + " AS related,CAST(NULL AS varchar) AS action,dt_inicio,dt_fim "
                + "FROM security." + table + " WHERE id_tenant=:tenant";
    }
    private static final String TENANT_GRANTS = actionGrant("usuario_tenant_role", "id_usuario_tenant", "MEMBER_ACTION")
            + " UNION ALL " + actionGrant("perfil_role", "id_perfil", "PROFILE_ACTION") + " UNION ALL " + actionGrant("grupo_role", "id_grupo", "GROUP_ACTION")
            + " UNION ALL " + linkGrant("usuario_tenant_perfil", "id_usuario_tenant", "id_perfil", "MEMBER_PROFILE")
            + " UNION ALL " + linkGrant("grupo_usuario", "id_grupo", "id_usuario_tenant", "GROUP_MEMBER")
            + " UNION ALL " + linkGrant("grupo_perfil", "id_grupo", "id_perfil", "GROUP_PROFILE");
    private static final String GLOBAL_GRANTS = "SELECT g.id_usuario_role_global AS id,'GLOBAL_ACTION' AS kind,g.id_usuario AS source,g.id_role AS related,r.cd_acao AS action,g.dt_inicio,g.dt_fim "
            + "FROM security.usuario_role_global g JOIN security.role_seguranca r ON r.id_role=g.id_role WHERE g.id_usuario=:user";
}
