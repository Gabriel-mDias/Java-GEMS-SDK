package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.FirebaseAuthException;
import br.com.gems.firebase.auth.SecurityEventSink;
import br.com.gems.firebase.auth.TenantAliasValidator;
import br.com.gems.security.authorization.AuthorizationCatalog;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Cadastro de tenants e memberships; readiness é metadado técnico separado da vigência. */
public final class JdbcTenantService implements br.com.gems.firebase.auth.TenantAdministration {
    private final SecurityJdbcSupport db;
    private final Clock clock;
    private final TenantAliasValidator aliases;
    private final AuthorizationCatalog catalog;
    private final JdbcActorAuthorization actors;
    public JdbcTenantService(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events, Clock clock, TenantAliasValidator aliases) {
        this(jdbc, tx, events, clock, aliases, new AuthorizationCatalog(Set.of(), Set.of()));
    }
    public JdbcTenantService(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events, Clock clock, TenantAliasValidator aliases, AuthorizationCatalog catalog) {
        this(jdbc, tx, events, clock, aliases, catalog, new br.com.gems.firebase.auth.SpringSecurityActorProvider());
    }
    public JdbcTenantService(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events, Clock clock, TenantAliasValidator aliases,
            AuthorizationCatalog catalog, br.com.gems.firebase.auth.SecurityActorProvider actorProvider) {
        db = new SecurityJdbcSupport(jdbc, tx, events, actorProvider); this.clock = clock; this.aliases = aliases;
        this.catalog = catalog;
        actors = new JdbcActorAuthorization(db, catalog);
    }
    /** Cria tenant PENDING sem DDL oculto. */
    public UUID create(String alias, String name) {
        String normalized = aliases.validate(alias);
        if (name == null || name.isBlank()) { throw new IllegalArgumentException("Nome obrigatório"); }
        return db.tx.execute(status -> {
            UUID id = UUID.randomUUID(); var now = clock.instant();
            actors.central("INSERIR_TENANT", now);
            db.jdbc.sql("INSERT INTO security.tenant(id_tenant,cd_alias,nm_tenant,dt_inicio) VALUES(:id,:alias,:name,:now)")
                    .param("id", id).param("alias", normalized).param("name", name).param("now", SecurityJdbcSupport.time(now)).update();
            db.event("TENANT_CREATED", null, id, now); return id;
        });
    }
    /** Membership novo; concessões encerradas permanecem encerradas. */
    public UUID addMember(UUID tenant, UUID user) {
        return addMember(tenant, user, catalog.tenantActions().contains("CONSULTAR_PROPRIO_CONTEXTO_TENANT") ? "CONSULTAR_PROPRIO_CONTEXTO_TENANT" : null);
    }
    /** Cria membership e concessão mínima do contexto atomicamente. */
    public UUID addMember(UUID tenant, UUID user, String selfAction) {
        if (selfAction != null && !catalog.tenantActions().contains(selfAction)) { throw FirebaseAuthException.denied(); }
        return db.tx.execute(status -> {
            var now = clock.instant(); UUID id = UUID.randomUUID();
            actors.tenant(tenant, now);
            if (selfAction != null && !selfAction.equals("CONSULTAR_PROPRIO_CONTEXTO_TENANT") && !db.actors.currentActor().bootstrap()) { throw FirebaseAuthException.denied(); }
            db.jdbc.sql("SELECT id_usuario FROM security.usuario WHERE id_usuario=:user AND dt_inicio<=:now "
                    + "AND (dt_fim IS NULL OR dt_fim>:now) FOR UPDATE").param("user", user).param("now", SecurityJdbcSupport.time(now))
                    .query(UUID.class).optional().orElseThrow(FirebaseAuthException::denied);
            db.jdbc.sql("SELECT id_tenant FROM security.tenant WHERE id_tenant=:tenant AND dt_inicio<=:now "
                    + "AND (dt_fim IS NULL OR dt_fim>:now) FOR UPDATE").param("tenant", tenant).param("now", SecurityJdbcSupport.time(now))
                    .query(UUID.class).optional().orElseThrow(FirebaseAuthException::denied);
            var existing = db.jdbc.sql("SELECT id_usuario_tenant FROM security.usuario_tenant WHERE id_tenant=:tenant AND id_usuario=:user AND dt_fim IS NULL")
                    .param("tenant", tenant).param("user", user).query(UUID.class).optional();
            if (existing.isPresent()) { return existing.get(); }
            db.jdbc.sql("INSERT INTO security.usuario_tenant(id_usuario_tenant,id_tenant,id_usuario,dt_inicio) VALUES(:id,:tenant,:user,:now)")
                    .param("id", id).param("tenant", tenant).param("user", user).param("now", SecurityJdbcSupport.time(now)).update();
            if (selfAction != null) {
                UUID role = db.jdbc.sql("SELECT id_role FROM security.role_seguranca WHERE cd_acao=:action AND cd_escopo='TENANT' "
                        + "AND dt_inicio<=:now AND (dt_fim IS NULL OR dt_fim>:now)").param("action", selfAction)
                        .param("now", SecurityJdbcSupport.time(now)).query(UUID.class).optional().orElseThrow(FirebaseAuthException::denied);
                UUID grant = UUID.randomUUID();
                db.jdbc.sql("INSERT INTO security.usuario_tenant_role(id_usuario_tenant_role,id_tenant,id_usuario_tenant,id_role,dt_inicio) VALUES(:id,:tenant,:member,:role,:now)")
                        .param("id", grant).param("tenant", tenant).param("member", id).param("role", role)
                        .param("now", SecurityJdbcSupport.time(now)).update();
                db.event("TENANT_SELF_ACTION_GRANTED", user, tenant, now, SecurityEventSink.TargetType.MEMBER_ACTION, grant, id, role, selfAction);
            }
            db.event("TENANT_MEMBER_ADDED", user, tenant, now, SecurityEventSink.TargetType.MEMBERSHIP, id, user, tenant, null); return id;
        });
    }
    /** Atualiza somente nome; alias e schema são imutáveis. */
    public void rename(UUID tenant, String name) {
        if (name == null || name.isBlank()) { throw new IllegalArgumentException("Nome obrigatório"); }
        db.tx.executeWithoutResult(status -> {
            actors.central("ALTERAR_TENANT", clock.instant());
            db.jdbc.sql("UPDATE security.tenant SET nm_tenant=:name WHERE id_tenant=:tenant")
                    .param("tenant", tenant).param("name", name).update(); db.event("TENANT_RENAMED", null, tenant, clock.instant());
        });
    }
    /** Encerra só a cadeia da membership selecionada. */
    public void endMembership(UUID tenant, UUID membership) {
        db.tx.executeWithoutResult(status -> {
            var now = clock.instant();
            actors.tenant(tenant, now);
            var user = db.jdbc.sql("SELECT id_usuario FROM security.usuario_tenant WHERE id_tenant=:tenant AND id_usuario_tenant=:membership FOR UPDATE")
                    .param("tenant", tenant).param("membership", membership).query(UUID.class).optional().orElseThrow(FirebaseAuthException::denied);
            for (String table : java.util.List.of("usuario_tenant_perfil", "usuario_tenant_role", "grupo_usuario", "usuario_tenant")) {
                db.jdbc.sql("UPDATE security." + table + " SET dt_fim=:now WHERE id_tenant=:tenant AND id_usuario_tenant=:membership AND dt_fim IS NULL")
                        .param("tenant", tenant).param("membership", membership).param("now", SecurityJdbcSupport.time(now)).update();
            }
            db.event("TENANT_MEMBERSHIP_ENDED", user, tenant, now, SecurityEventSink.TargetType.MEMBERSHIP, membership, user, tenant, null);
        });
    }
    /** Suspensão mantém inventário READY disponível para migrations. */
    public void suspend(UUID tenant) {
        db.tx.executeWithoutResult(status -> {
            var now = clock.instant();
            actors.central("SUSPENDER_TENANT", now);
            db.jdbc.sql("UPDATE security.tenant SET dt_fim=:now WHERE id_tenant=:tenant AND dt_fim IS NULL")
                    .param("tenant", tenant).param("now", SecurityJdbcSupport.time(now)).update();
            db.event("TENANT_SUSPENDED", null, tenant, now);
        });
    }
    /** Inventário técnico contém nomes de schema READY inclusive tenants suspensos. */
    public Set<String> expectedSchemas() {
        return db.jdbc.sql("SELECT cd_alias FROM security.tenant WHERE cd_provisionamento='READY'")
                .query(String.class).list().stream().map(aliases::validate).map(alias -> "tenant_" + alias).collect(Collectors.toUnmodifiableSet());
    }
}
