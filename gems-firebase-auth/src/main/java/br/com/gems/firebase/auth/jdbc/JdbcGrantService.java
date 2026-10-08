package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.DelegationPolicy;
import br.com.gems.firebase.auth.FirebaseAuthException;
import br.com.gems.firebase.auth.SecurityEventSink;
import br.com.gems.security.authorization.AuthorizationCatalog;
import java.time.Clock;
import java.util.UUID;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Perfis/grupos e concessões tipadas; o consumidor autoriza a operação na fronteira do serviço. */
public final class JdbcGrantService implements br.com.gems.firebase.auth.GrantAdministration {
    @Override public UUID grant(br.com.gems.firebase.auth.GrantAdministration.Target target, UUID tenant, UUID owner, String action) {
        return grant(Target.valueOf(target.name()), tenant, owner, action);
    }
    @Override public UUID link(br.com.gems.firebase.auth.GrantAdministration.Link link, UUID tenant, UUID left, UUID right) {
        return link(Link.valueOf(link.name()), tenant, left, right);
    }
    @Override public void revoke(br.com.gems.firebase.auth.GrantAdministration.Target target, UUID tenant, UUID id) {
        revoke(Target.valueOf(target.name()), tenant, id);
    }
    @Override public void revoke(br.com.gems.firebase.auth.GrantAdministration.Link link, UUID tenant, UUID id) {
        revoke(Link.valueOf(link.name()), tenant, id);
    }
    /** Ligações possíveis, sem tabela SQL livre vinda do consumidor. */
    public enum Link {
        MEMBER_PROFILE("usuario_tenant_perfil", "id_usuario_tenant", "id_perfil"),
        GROUP_MEMBER("grupo_usuario", "id_grupo", "id_usuario_tenant"),
        GROUP_PROFILE("grupo_perfil", "id_grupo", "id_perfil");
        final String table; final String left; final String right;
        Link(String table, String left, String right) { this.table = table; this.left = left; this.right = right; }
    }
    /** Destino de uma ação tenant delegável. */
    public enum Target {
        MEMBER("usuario_tenant_role", "id_usuario_tenant"), PROFILE("perfil_role", "id_perfil"), GROUP("grupo_role", "id_grupo");
        final String table; final String owner;
        Target(String table, String owner) { this.table = table; this.owner = owner; }
    }
    private final SecurityJdbcSupport db;
    private final AuthorizationCatalog catalog;
    private final DelegationPolicy delegation;
    private final Clock clock;
    private final JdbcActorAuthorization actors;
    public JdbcGrantService(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events,
            AuthorizationCatalog catalog, DelegationPolicy delegation, Clock clock) {
        this(jdbc, tx, events, catalog, delegation, clock, new br.com.gems.firebase.auth.SpringSecurityActorProvider());
    }
    public JdbcGrantService(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events,
            AuthorizationCatalog catalog, DelegationPolicy delegation, Clock clock, br.com.gems.firebase.auth.SecurityActorProvider actorProvider) {
        db = new SecurityJdbcSupport(jdbc, tx, events, actorProvider); this.catalog = catalog; this.delegation = delegation; this.clock = clock;
        actors = new JdbcActorAuthorization(db, catalog);
    }
    /** Cria perfil com intervalo imediato. */
    public UUID createProfile(UUID tenant, String code, String name) { return create(tenant, code, name, "perfil"); }
    /** Cria grupo com intervalo imediato. */
    public UUID createGroup(UUID tenant, String code, String name) { return create(tenant, code, name, "grupo"); }
    /** Atualiza apresentação do perfil sem modificar vínculos/histórico. */
    public void updateProfile(UUID tenant, UUID profile, String name, String description) { updateContainer("perfil", tenant, profile, name, description); }
    /** Atualiza apresentação do grupo sem modificar vínculos/histórico. */
    public void updateGroup(UUID tenant, UUID group, String name, String description) { updateContainer("grupo", tenant, group, name, description); }
    private void updateContainer(String table, UUID tenant, UUID id, String name, String description) {
        if (name == null || name.isBlank()) { throw new IllegalArgumentException("Nome obrigatório"); }
        db.tx.executeWithoutResult(status -> {
            actors.tenant(tenant, clock.instant());
            requireOwner("id_" + table, tenant, id, clock.instant());
            db.jdbc.sql("UPDATE security." + table + " SET nm_" + table + "=:name,ds_" + table + "=:description WHERE id_tenant=:tenant AND id_" + table + "=:id")
                    .param("tenant", tenant).param("id", id).param("name", name).param("description", description).update();
            db.event("AUTHORIZATION_CONTAINER_UPDATED", null, tenant, clock.instant(), containerType(table), id, null, null, null);
        });
    }
    private UUID create(UUID tenant, String code, String name, String table) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) { throw new IllegalArgumentException("Cadastro inválido"); }
        return db.tx.execute(status -> {
            UUID id = UUID.randomUUID(); var now = clock.instant();
            actors.tenant(tenant, now);
            db.jdbc.sql("INSERT INTO security." + table + "(id_" + table + ",id_tenant,cd_" + table + ",nm_" + table
                    + ",dt_inicio) VALUES(:id,:tenant,:code,:name,:now)").param("id", id).param("tenant", tenant)
                    .param("code", code).param("name", name).param("now", SecurityJdbcSupport.time(now)).update();
            db.event("AUTHORIZATION_CONTAINER_CREATED", null, tenant, now, containerType(table), id, null, null, null); return id;
        });
    }
    /** Liga membership/perfil/grupo; composite FKs obrigam o mesmo tenant. */
    public UUID link(Link link, UUID tenant, UUID left, UUID right) {
        return db.tx.execute(status -> {
            UUID id = UUID.randomUUID(); var now = clock.instant();
            Set<String> allowed = actors.tenant(tenant, now);
            if (link == Link.GROUP_MEMBER) {
                requireOwner(link.right, tenant, right, now); requireOwner(link.left, tenant, left, now);
            } else {
                requireOwner(link.left, tenant, left, now); requireOwner(link.right, tenant, right, now);
            }
            UUID container = link == Link.MEMBER_PROFILE ? right : link == Link.GROUP_MEMBER ? left : right;
            requireDelegable(containerActions(link == Link.GROUP_MEMBER ? "grupo" : "perfil", tenant, container, now), allowed);
            var existing = db.jdbc.sql("SELECT id_" + link.table + " FROM security." + link.table + " WHERE id_tenant=:tenant AND "
                    + link.left + "=:left AND " + link.right + "=:right AND dt_fim IS NULL")
                    .param("tenant", tenant).param("left", left).param("right", right).query(UUID.class).optional();
            if (existing.isPresent()) { return existing.get(); }
            db.jdbc.sql("INSERT INTO security." + link.table + "(id_" + link.table + ",id_tenant," + link.left + "," + link.right
                    + ",dt_inicio) VALUES(:id,:tenant,:left,:right,:now)").param("id", id).param("tenant", tenant)
                    .param("left", left).param("right", right).param("now", SecurityJdbcSupport.time(now)).update();
            db.event("AUTHORIZATION_LINK_GRANTED", null, tenant, now, SecurityEventSink.TargetType.valueOf(link.name()), id, left, right, null); return id;
        });
    }
    /** Concede somente ações presentes e delegáveis no catálogo do consumidor. */
    public UUID grant(Target target, UUID tenant, UUID owner, String action) {
        if (!catalog.tenantActions().contains(action) || !delegation.delegableTenantActions().contains(action)) {
            throw FirebaseAuthException.denied();
        }
        return db.tx.execute(status -> {
            UUID id = UUID.randomUUID(); var now = clock.instant();
            requireDelegable(Set.of(action), actors.tenant(tenant, now));
            requireOwner(target.owner, tenant, owner, now);
            UUID role = role(action, "TENANT", now);
            var existing = db.jdbc.sql("SELECT id_" + target.table + " FROM security." + target.table + " WHERE id_tenant=:tenant AND "
                    + target.owner + "=:owner AND id_role=:role AND dt_fim IS NULL")
                    .param("tenant", tenant).param("owner", owner).param("role", role).query(UUID.class).optional();
            if (existing.isPresent()) { return existing.get(); }
            db.jdbc.sql("INSERT INTO security." + target.table + "(id_" + target.table + ",id_tenant," + target.owner
                    + ",id_role,dt_inicio) VALUES(:id,:tenant,:owner,:role,:now)").param("id", id).param("tenant", tenant)
                    .param("owner", owner).param("role", role).param("now", SecurityJdbcSupport.time(now)).update();
            db.event("TENANT_ACTION_GRANTED", null, tenant, now, SecurityEventSink.TargetType.valueOf(target.name() + "_ACTION"), id, owner, role, action); return id;
        });
    }
    /** Operação central explícita; ações globais nunca atravessam grants tenant. */
    public UUID grantGlobal(UUID user, String action) {
        if (!catalog.globalActions().contains(action)) { throw FirebaseAuthException.denied(); }
        return db.tx.execute(status -> {
            UUID id = UUID.randomUUID(); var now = clock.instant();
            actors.central("CONCEDER_ACAO_GLOBAL", now);
            requireUser(user, now);
            UUID role = role(action, "GLOBAL", now);
            var existing = db.jdbc.sql("SELECT id_usuario_role_global FROM security.usuario_role_global WHERE id_usuario=:user AND id_role=:role AND dt_fim IS NULL")
                    .param("user", user).param("role", role).query(UUID.class).optional();
            if (existing.isPresent()) { return existing.get(); }
            db.jdbc.sql("INSERT INTO security.usuario_role_global(id_usuario_role_global,id_usuario,id_role,dt_inicio) VALUES(:id,:user,:role,:now)")
                    .param("id", id).param("user", user).param("role", role)
                    .param("now", SecurityJdbcSupport.time(now)).update();
            db.event("GLOBAL_ACTION_GRANTED", user, null, now, SecurityEventSink.TargetType.GLOBAL_ACTION, id, user, null, action); return id;
        });
    }
    /** Encerra ligação; nova concessão precisa outra linha. */
    public void revoke(Link link, UUID tenant, UUID id) { end(link.table, tenant, id); }
    /** Encerra ação tenant sem cache. */
    public void revoke(Target target, UUID tenant, UUID id) { end(target.table, tenant, id); }
    /** Suspende perfil e corta toda herança no próximo snapshot. */
    public void suspendProfile(UUID tenant, UUID profile) { end("perfil", tenant, profile); }
    /** Suspende grupo e corta toda herança no próximo snapshot. */
    public void suspendGroup(UUID tenant, UUID group) { end("grupo", tenant, group); }
    /** Encerra ação global. */
    public void revokeGlobal(UUID user, UUID grant) {
        db.tx.executeWithoutResult(status -> {
            var now = clock.instant();
            actors.central("REVOGAR_ACAO_GLOBAL", now);
            db.jdbc.sql("UPDATE security.usuario_role_global SET dt_fim=:now WHERE id_usuario=:user AND id_usuario_role_global=:id AND dt_fim IS NULL")
                    .param("user", user).param("id", grant).param("now", SecurityJdbcSupport.time(now)).update();
            var action = db.jdbc.sql("SELECT r.cd_acao FROM security.usuario_role_global g JOIN security.role_seguranca r ON r.id_role=g.id_role "
                    + "WHERE g.id_usuario=:user AND g.id_usuario_role_global=:id").param("user", user).param("id", grant).query(String.class).optional().orElse(null);
            db.event("GLOBAL_ACTION_REVOKED", user, null, now, SecurityEventSink.TargetType.GLOBAL_ACTION, grant, user, null, action);
        });
    }
    private void end(String table, UUID tenant, UUID id) {
        db.tx.executeWithoutResult(status -> {
            var now = clock.instant();
            actors.tenant(tenant, now);
            var target = db.jdbc.sql("SELECT * FROM security." + table + " WHERE id_tenant=:tenant AND id_" + table + "=:id FOR UPDATE")
                    .param("tenant", tenant).param("id", id).query().listOfRows().stream().findFirst().orElseThrow(FirebaseAuthException::denied);
            db.jdbc.sql("UPDATE security." + table + " SET dt_fim=:now WHERE id_tenant=:tenant AND id_" + table + "=:id AND dt_fim IS NULL")
                    .param("tenant", tenant).param("id", id).param("now", SecurityJdbcSupport.time(now)).update();
            String action = target.containsKey("id_role") ? db.jdbc.sql("SELECT cd_acao FROM security.role_seguranca WHERE id_role=:id")
                    .param("id", target.get("id_role")).query(String.class).single() : null;
            db.event("TENANT_AUTHORIZATION_ENDED", null, tenant, now, eventType(table), id,
                    (UUID) target.get(ownerColumn(table)), (UUID) target.get(relatedColumn(table)), action);
        });
    }
    private Set<String> containerActions(String table, UUID tenant, UUID id, java.time.Instant now) {
        String sql = "SELECT r.cd_acao FROM security." + table + "_role g JOIN security.role_seguranca r ON r.id_role=g.id_role "
                + "WHERE g.id_tenant=:tenant AND g.id_" + table + "=:id AND g.dt_inicio<=:now AND (g.dt_fim IS NULL OR g.dt_fim>:now) "
                + "AND r.dt_inicio<=:now AND (r.dt_fim IS NULL OR r.dt_fim>:now)";
        if (table.equals("grupo")) {
            sql += " UNION SELECT r.cd_acao FROM security.grupo_perfil gp JOIN security.perfil p ON p.id_tenant=gp.id_tenant AND p.id_perfil=gp.id_perfil "
                    + "JOIN security.perfil_role pr ON pr.id_tenant=p.id_tenant AND pr.id_perfil=p.id_perfil JOIN security.role_seguranca r ON r.id_role=pr.id_role "
                    + "WHERE gp.id_tenant=:tenant AND gp.id_grupo=:id AND gp.dt_inicio<=:now AND (gp.dt_fim IS NULL OR gp.dt_fim>:now) "
                    + "AND p.dt_inicio<=:now AND (p.dt_fim IS NULL OR p.dt_fim>:now) AND pr.dt_inicio<=:now AND (pr.dt_fim IS NULL OR pr.dt_fim>:now) "
                    + "AND r.dt_inicio<=:now AND (r.dt_fim IS NULL OR r.dt_fim>:now)";
        }
        return Set.copyOf(db.jdbc.sql(sql).param("tenant", tenant).param("id", id).param("now", SecurityJdbcSupport.time(now)).query(String.class).list());
    }
    private void requireDelegable(Set<String> requested, Set<String> actorActions) {
        if (!catalog.tenantActions().containsAll(requested) || !delegation.delegableTenantActions().containsAll(requested)
                || !actorActions.containsAll(requested)) { throw FirebaseAuthException.denied(); }
    }
    private static SecurityEventSink.TargetType containerType(String table) { return table.equals("perfil") ? SecurityEventSink.TargetType.PROFILE : SecurityEventSink.TargetType.GROUP; }
    private static SecurityEventSink.TargetType eventType(String table) {
        return switch (table) {
            case "usuario_tenant_perfil" -> SecurityEventSink.TargetType.MEMBER_PROFILE;
            case "grupo_usuario" -> SecurityEventSink.TargetType.GROUP_MEMBER;
            case "grupo_perfil" -> SecurityEventSink.TargetType.GROUP_PROFILE;
            case "usuario_tenant_role" -> SecurityEventSink.TargetType.MEMBER_ACTION;
            case "perfil_role" -> SecurityEventSink.TargetType.PROFILE_ACTION;
            case "grupo_role" -> SecurityEventSink.TargetType.GROUP_ACTION;
            default -> containerType(table);
        };
    }
    private static String ownerColumn(String table) {
        return switch (table) { case "usuario_tenant_perfil", "usuario_tenant_role" -> "id_usuario_tenant";
            case "perfil_role" -> "id_perfil"; case "grupo_usuario", "grupo_perfil", "grupo_role" -> "id_grupo"; default -> "id_" + table; };
    }
    private static String relatedColumn(String table) {
        return switch (table) { case "usuario_tenant_perfil", "grupo_perfil" -> "id_perfil"; case "grupo_usuario" -> "id_usuario_tenant";
            case "usuario_tenant_role", "perfil_role", "grupo_role" -> "id_role"; default -> "id_" + table; };
    }
    private UUID role(String action, String scope, java.time.Instant now) {
        return db.jdbc.sql("SELECT id_role FROM security.role_seguranca WHERE cd_acao=:action AND cd_escopo=:scope "
                + "AND dt_inicio<=:now AND (dt_fim IS NULL OR dt_fim>:now)").param("action", action).param("scope", scope)
                .param("now", SecurityJdbcSupport.time(now)).query(UUID.class).optional().orElseThrow(FirebaseAuthException::denied);
    }
    private void requireUser(UUID user, java.time.Instant now) {
        db.jdbc.sql("SELECT id_usuario FROM security.usuario WHERE id_usuario=:user AND dt_inicio<=:now "
                + "AND (dt_fim IS NULL OR dt_fim>:now) FOR UPDATE").param("user", user).param("now", SecurityJdbcSupport.time(now))
                .query(UUID.class).optional().orElseThrow(FirebaseAuthException::denied);
    }
    private void requireOwner(String column, UUID tenant, UUID owner, java.time.Instant now) {
        String table = switch (column) {
            case "id_usuario_tenant" -> "usuario_tenant";
            case "id_perfil" -> "perfil";
            case "id_grupo" -> "grupo";
            default -> throw new IllegalArgumentException("Destino inválido");
        };
        if (table.equals("usuario_tenant")) {
            UUID user = db.jdbc.sql("SELECT id_usuario FROM security.usuario_tenant WHERE id_tenant=:tenant AND id_usuario_tenant=:id")
                    .param("tenant", tenant).param("id", owner).query(UUID.class).optional().orElseThrow(FirebaseAuthException::denied);
            requireUser(user, now);
        }
        db.jdbc.sql("SELECT " + column + " FROM security." + table + " WHERE id_tenant=:tenant AND " + column
                + "=:id AND dt_inicio<=:now AND (dt_fim IS NULL OR dt_fim>:now) FOR UPDATE")
                .param("tenant", tenant).param("id", owner).param("now", SecurityJdbcSupport.time(now)).query(UUID.class)
                .optional().orElseThrow(FirebaseAuthException::denied);
    }
}
