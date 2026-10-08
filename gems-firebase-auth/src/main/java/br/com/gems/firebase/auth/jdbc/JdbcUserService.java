package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.FirebaseAuthException;
import br.com.gems.firebase.auth.SecurityEventSink;
import br.com.gems.security.authorization.AuthorizationCatalog;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Cadastro e lifecycle local; nenhum método faz chamada externa dentro da transação. */
public final class JdbcUserService implements br.com.gems.firebase.auth.UserAdministration {
    private final SecurityJdbcSupport db;
    private final Clock clock;
    private final String project;
    private final AuthorizationCatalog catalog;
    private final JdbcActorAuthorization actors;
    public JdbcUserService(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events, Clock clock, String project) {
        this(jdbc, tx, events, clock, project, new AuthorizationCatalog(java.util.Set.of(), java.util.Set.of()));
    }
    public JdbcUserService(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events, Clock clock, String project, AuthorizationCatalog catalog) {
        this(jdbc, tx, events, clock, project, catalog, new br.com.gems.firebase.auth.SpringSecurityActorProvider());
    }
    public JdbcUserService(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events, Clock clock, String project,
            AuthorizationCatalog catalog, br.com.gems.firebase.auth.SecurityActorProvider actorProvider) {
        db = new SecurityJdbcSupport(jdbc, tx, events, actorProvider); this.clock = clock; this.project = project;
        this.catalog = catalog;
        actors = new JdbcActorAuthorization(db, catalog);
    }
    /** Pré-provisiona Google; email não é chave de identidade nem grant implícito. */
    public UUID preProvisionGoogle(String name, String email) { return create(name, email, false, defaultSelfAction()); }
    /** Concessão de própria conta entra na mesma transação do cadastro. */
    public UUID preProvisionGoogle(String name, String email, String selfAction) { return create(name, email, false, selfAction); }
    /** Planeja um UID novo; envio de email/reset pertence ao cliente Firebase. */
    public UUID provisionPassword(String name, String email) { return create(name, email, true, defaultSelfAction()); }
    /** Variante com ação de própria conta declarada no catálogo do consumidor. */
    public UUID provisionPassword(String name, String email, String selfAction) { return create(name, email, true, selfAction); }
    private String defaultSelfAction() { return catalog.globalActions().contains("CONSULTAR_PROPRIA_CONTA") ? "CONSULTAR_PROPRIA_CONTA" : null; }
    private UUID create(String name, String email, boolean password, String selfAction) {
        if (selfAction != null && !catalog.globalActions().contains(selfAction)) { throw FirebaseAuthException.denied(); }
        if (name == null || name.isBlank() || email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Cadastro inválido");
        }
        return db.tx.execute(status -> {
            UUID id = UUID.randomUUID(); Instant now = clock.instant();
            actors.central("INSERIR_USUARIO", now);
            emailLock(email);
            if (selfAction != null && !selfAction.equals("CONSULTAR_PROPRIA_CONTA") && !db.actors.currentActor().bootstrap()) { throw FirebaseAuthException.denied(); }
            String uid = password ? UUID.randomUUID().toString() : null;
            db.jdbc.sql("INSERT INTO security.usuario(id_usuario,nm_usuario,cd_email,cd_elegibilidade,cd_uid_planejado,"
                    + "nr_versao_identidade,dt_criacao,dt_alteracao,dt_inicio) "
                    + "VALUES(:id,:name,:email,:eligibility,:uid,:version,:now,:now,:now)")
                    .param("id", id).param("name", name).param("email", email.strip().toLowerCase(Locale.ROOT))
                    .param("eligibility", password ? "PASSWORD_PROVISIONING" : "GOOGLE_PENDING")
                    .param("uid", uid).param("version", password ? 1 : 0).param("now", SecurityJdbcSupport.time(now)).update();
            if (password) { command(id, uid, "CREATE", 1, now); }
            if (selfAction != null) {
                UUID role = db.jdbc.sql("SELECT id_role FROM security.role_seguranca WHERE cd_acao=:action AND cd_escopo='GLOBAL' "
                        + "AND dt_inicio<=:now AND (dt_fim IS NULL OR dt_fim>:now)").param("action", selfAction)
                        .param("now", SecurityJdbcSupport.time(now)).query(UUID.class).optional().orElseThrow(FirebaseAuthException::denied);
                UUID grant = UUID.randomUUID();
                db.jdbc.sql("INSERT INTO security.usuario_role_global(id_usuario_role_global,id_usuario,id_role,dt_inicio) VALUES(:id,:user,:role,:now)")
                        .param("id", grant).param("user", id).param("role", role).param("now", SecurityJdbcSupport.time(now)).update();
                db.event("GLOBAL_SELF_ACTION_GRANTED", id, null, now, SecurityEventSink.TargetType.GLOBAL_ACTION, grant, id, role, selfAction);
            }
            db.event("USER_PREPROVISIONED", id, null, now);
            return id;
        });
    }
    /** Bloqueia SQL e encerra concessões antes de qualquer DISABLE/REVOKE remoto. */
    public void suspend(UUID user) {
        db.tx.executeWithoutResult(status -> {
            lock(user); Instant now = clock.instant();
            actors.central("SUSPENDER_USUARIO", now);
            var state = state(user);
            if (state.end() != null && db.jdbc.sql("SELECT id_comando FROM security.comando_identidade WHERE id_usuario=:user "
                    + "AND nr_versao=:version AND cd_tipo='ENABLE' AND cd_estado IN ('PENDING','RETRY','CLAIMED')")
                    .param("user", user).param("version", state.version()).query(UUID.class).list().isEmpty()) { return; }
            long version = state.version() + 1;
            db.jdbc.sql("UPDATE security.usuario SET dt_fim=COALESCE(dt_fim,:now),dt_alteracao=:now,nr_versao_identidade=:version WHERE id_usuario=:user")
                    .param("user", user).param("version", version).param("now", SecurityJdbcSupport.time(now)).update();
            endUserGrants(user, now);
            String uid = uid(user);
            if (uid != null) {
                command(user, uid, "DISABLE", version, now);
                command(user, uid, "REVOKE", version, now);
            }
            db.event("USER_SUSPENDED", user, null, now);
        });
    }
    /** Solicita ENABLE versionado; vigência local só muda depois do ACK atual. */
    public void requestReactivation(UUID user) {
        db.tx.executeWithoutResult(status -> {
            lock(user); var state = state(user); Instant now = clock.instant();
            actors.central("REATIVAR_USUARIO", now);
            if (state.end() == null) { return; }
            String uid = db.jdbc.sql("SELECT cd_sujeito_externo FROM security.usuario_identidade WHERE id_usuario=:user AND dt_fim IS NULL")
                    .param("user", user).query(String.class).optional().orElseThrow(FirebaseAuthException::denied);
            if (!"BOUND".equals(state.eligibility())) { throw FirebaseAuthException.denied(); }
            var pending = db.jdbc.sql("SELECT id_comando FROM security.comando_identidade WHERE id_usuario=:user "
                    + "AND nr_versao=:version AND cd_tipo='ENABLE' AND cd_estado IN ('PENDING','RETRY','CLAIMED')")
                    .param("user", user).param("version", state.version()).query(UUID.class).list();
            if (!pending.isEmpty()) { return; }
            long version = state.version() + 1;
            db.jdbc.sql("UPDATE security.usuario SET nr_versao_identidade=:version,dt_alteracao=:now WHERE id_usuario=:user")
                    .param("user", user).param("version", version).param("now", SecurityJdbcSupport.time(now)).update();
            command(user, uid, "ENABLE", version, now);
            db.event("USER_REACTIVATION_REQUESTED", user, null, now);
        });
    }
    /** Atualiza campos informativos locais sem transferir UID/owner. */
    public void update(UUID user, String name, String email) {
        if (name == null || name.isBlank() || email == null || !email.contains("@")) { throw new IllegalArgumentException("Cadastro inválido"); }
        db.tx.executeWithoutResult(status -> {
            lock(user);
            String previous = db.jdbc.sql("SELECT cd_email FROM security.usuario WHERE id_usuario=:user")
                    .param("user", user).query(String.class).optional().orElseThrow(FirebaseAuthException::denied);
            java.util.stream.Stream.of(previous, email.strip().toLowerCase(Locale.ROOT)).distinct().sorted().forEach(this::emailLock);
            var current = state(user); var now = clock.instant();
            actors.central("ALTERAR_USUARIO", now);
            if ("PASSWORD_PROVISIONING".equals(current.eligibility())) {
                throw new IllegalStateException("ACCOUNT_PROVISIONING_IN_PROGRESS");
            }
            db.jdbc.sql("UPDATE security.usuario SET nm_usuario=:name,cd_email=:email,dt_alteracao=:now WHERE id_usuario=:user")
                    .param("user", user).param("name", name).param("email", email.strip().toLowerCase(Locale.ROOT))
                    .param("now", SecurityJdbcSupport.time(now)).update(); db.event("USER_UPDATED", user, null, now);
        });
    }
    /** Cancela ENABLE pendente por nova suspensão mesmo se o usuário ainda está fechado. */
    public void cancelReactivation(UUID user) {
        db.tx.executeWithoutResult(status -> {
            lock(user); var state = state(user); Instant now = clock.instant();
            actors.central("SUSPENDER_USUARIO", now);
            if (state.end() == null) { throw new IllegalStateException("Use suspend para usuário vigente"); }
            long version = state.version() + 1;
            db.jdbc.sql("UPDATE security.usuario SET nr_versao_identidade=:version,dt_alteracao=:now WHERE id_usuario=:user")
                    .param("user", user).param("version", version).param("now", SecurityJdbcSupport.time(now)).update();
            String uid = uid(user);
            if (uid != null) { command(user, uid, "DISABLE", version, now); command(user, uid, "REVOKE", version, now); }
            db.event("USER_REACTIVATION_CANCELLED", user, null, now);
        });
    }
    private void endUserGrants(UUID user, Instant now) {
        for (String table : List.of("usuario_tenant_perfil", "usuario_tenant_role", "grupo_usuario")) {
            db.jdbc.sql("UPDATE security." + table + " SET dt_fim=:now WHERE dt_fim IS NULL AND id_usuario_tenant IN "
                    + "(SELECT id_usuario_tenant FROM security.usuario_tenant WHERE id_usuario=:user)")
                    .param("user", user).param("now", SecurityJdbcSupport.time(now)).update();
        }
        for (String table : List.of("usuario_role_global", "usuario_tenant")) {
            db.jdbc.sql("UPDATE security." + table + " SET dt_fim=:now WHERE dt_fim IS NULL AND id_usuario=:user")
                    .param("user", user).param("now", SecurityJdbcSupport.time(now)).update();
        }
    }
    private State state(UUID user) {
        return db.jdbc.sql("SELECT nr_versao_identidade,dt_fim,cd_elegibilidade FROM security.usuario WHERE id_usuario=:user FOR UPDATE")
                .param("user", user).query((rs, row) -> new State(rs.getLong(1),
                        rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant(), rs.getString(3)))
                .optional().orElseThrow(FirebaseAuthException::denied);
    }
    private String uid(UUID user) {
        var linked = db.jdbc.sql("SELECT cd_sujeito_externo FROM security.usuario_identidade WHERE id_usuario=:user AND dt_fim IS NULL")
                .param("user", user).query(String.class).optional();
        return linked.orElseGet(() -> db.jdbc.sql("SELECT cd_uid_planejado FROM security.usuario WHERE id_usuario=:user")
                .param("user", user).query(String.class).optional().orElse(null));
    }
    private void lock(UUID user) {
        db.jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))").param("key", "firebase-user:" + user)
                .query().listOfRows();
    }
    private void emailLock(String email) {
        db.jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:email,0))")
                .param("email", email.strip().toLowerCase(Locale.ROOT)).query().listOfRows();
    }
    private void command(UUID user, String uid, String type, long version, Instant now) {
        db.jdbc.sql("INSERT INTO security.comando_identidade(id_comando,id_usuario,cd_projeto,cd_emissor,cd_uid,cd_tipo,"
                + "nr_versao,cd_idempotencia,dt_criacao,dt_alteracao,dt_proxima_tentativa) VALUES(:id,:user,:project,:issuer,:uid,:type,:version,:key,:now,:now,:now)")
                .param("id", UUID.randomUUID()).param("user", user).param("project", project)
                .param("issuer", "https://securetoken.google.com/" + project).param("uid", uid).param("type", type)
                .param("version", version).param("key", user + ":" + version + ":" + type)
                .param("now", SecurityJdbcSupport.time(now)).update();
    }
    private record State(long version, Instant end, String eligibility) { }
}
