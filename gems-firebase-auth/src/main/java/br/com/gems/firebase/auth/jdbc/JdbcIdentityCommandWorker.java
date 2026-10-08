package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.FirebaseAdminGateway;
import br.com.gems.firebase.auth.SecurityEventSink;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Worker explícito com lease durável, exclusão por usuário e ACK por CAS da versão atual. */
public final class JdbcIdentityCommandWorker {
    private final SecurityJdbcSupport db;
    private final DataSource dataSource;
    private final FirebaseAdminGateway admin;
    private final Clock clock;
    private final Duration lease;
    public JdbcIdentityCommandWorker(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events,
            DataSource dataSource, FirebaseAdminGateway admin, Clock clock, Duration lease) {
        if (lease.isNegative() || lease.isZero()) { throw new IllegalArgumentException("Lease inválido"); }
        db = new SecurityJdbcSupport(jdbc, tx, events); this.dataSource = dataSource;
        this.admin = admin; this.clock = clock; this.lease = lease;
    }
    /** Processa no máximo um comando; o consumidor controla scheduler/retries, fora de HTTP. */
    public boolean runOnce() {
        Command command = claim();
        if (command == null) { return false; }
        try (Connection session = dataSource.getConnection()) {
            boolean locked = false;
            try {
                lock(session, command.user(), true); locked = true;
                if (!current(command)) { finish(command, "OBSOLETE", "SUPERSEDED"); return true; }
                FirebaseAdminGateway.User created = null;
                switch (command.type()) {
                    case "CREATE" -> {
                        var account = db.jdbc.sql("SELECT cd_email,nm_usuario FROM security.usuario WHERE id_usuario=:user")
                                .param("user", command.user()).query((rs, row) -> new Account(rs.getString(1), rs.getString(2))).single();
                        created = admin.createUser(command.uid(), account.email(), account.name());
                        if (!command.uid().equals(created.uid()) || !account.email().equals(created.email()) || created.disabled()) {
                            throw new IllegalStateException("REMOTE_ACCOUNT_MISMATCH");
                        }
                    }
                    case "DISABLE" -> admin.setDisabled(command.uid(), true);
                    case "REVOKE" -> admin.revokeRefreshTokens(command.uid());
                    case "ENABLE" -> admin.setDisabled(command.uid(), false);
                    default -> throw new IllegalStateException("COMMAND_TYPE_INVALID");
                }
                acknowledge(command, created);
            } catch (RuntimeException failure) {
                finish(command, "RETRY", "PROVIDER_OPERATION_FAILED");
            } finally {
                if (locked) {
                    try { lock(session, command.user(), false); }
                    catch (SQLException failure) {
                        session.abort(Runnable::run);
                        throw failure;
                    }
                }
            }
        } catch (SQLException failure) {
            finish(command, "RETRY", "COMMAND_SESSION_FAILED");
        }
        return true;
    }
    private Command claim() {
        return db.tx.execute(status -> {
            Instant now = clock.instant(); UUID claim = UUID.randomUUID();
            var found = db.jdbc.sql("SELECT id_comando,id_usuario,cd_projeto,cd_emissor,cd_uid,cd_tipo,nr_versao "
                    + "FROM security.comando_identidade c WHERE ((cd_estado IN ('PENDING','RETRY') AND dt_proxima_tentativa<=:now) "
                    + "OR (cd_estado='CLAIMED' AND dt_lease<=:now)) "
                    + "AND (cd_tipo<>'ENABLE' OR NOT EXISTS (SELECT 1 FROM security.comando_identidade r "
                    + "WHERE r.id_usuario=c.id_usuario AND r.cd_tipo='REVOKE' AND r.nr_versao<c.nr_versao AND r.cd_estado<>'ACKED')) "
                    + "ORDER BY dt_proxima_tentativa,dt_criacao,id_comando LIMIT 1 FOR UPDATE SKIP LOCKED")
                    .param("now", SecurityJdbcSupport.time(now)).query((rs, row) -> new Command(rs.getObject(1, UUID.class),
                            rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                            rs.getLong(7), claim)).optional();
            if (found.isEmpty()) { return null; }
            db.jdbc.sql("UPDATE security.comando_identidade SET cd_estado='CLAIMED',cd_claim=:claim,dt_lease=:lease,"
                    + "nr_tentativas=nr_tentativas+1,dt_alteracao=:now WHERE id_comando=:id")
                    .param("id", found.get().id()).param("claim", claim).param("lease", SecurityJdbcSupport.time(now.plus(lease)))
                    .param("now", SecurityJdbcSupport.time(now)).update();
            return found.get();
        });
    }
    private boolean current(Command command) {
        return db.jdbc.sql("SELECT c.id_comando FROM security.comando_identidade c JOIN security.usuario u ON u.id_usuario=c.id_usuario "
                + "JOIN security.provedor_identidade p ON p.cd_projeto=c.cd_projeto AND p.cd_emissor=c.cd_emissor "
                + "WHERE c.id_comando=:id AND c.cd_claim=:claim AND c.cd_estado='CLAIMED' "
                + "AND c.dt_lease>:now AND (c.cd_tipo='REVOKE' OR u.nr_versao_identidade=c.nr_versao) "
                + "AND (c.cd_tipo<>'ENABLE' OR NOT EXISTS (SELECT 1 FROM security.comando_identidade r "
                + "WHERE r.id_usuario=c.id_usuario AND r.cd_tipo='REVOKE' AND r.nr_versao<c.nr_versao AND r.cd_estado<>'ACKED')) "
                + "AND p.dt_inicio<=:now AND (p.dt_fim IS NULL OR p.dt_fim>:now)")
                .param("id", command.id()).param("claim", command.claim()).param("now", SecurityJdbcSupport.time(clock.instant()))
                .query(UUID.class).optional().isPresent();
    }
    private void acknowledge(Command command, FirebaseAdminGateway.User created) {
        db.tx.executeWithoutResult(status -> {
            db.jdbc.sql("SELECT id_usuario FROM security.usuario WHERE id_usuario=:user FOR UPDATE")
                    .param("user", command.user()).query(UUID.class).single();
            if (!current(command)) { finishInside(command, "OBSOLETE", "SUPERSEDED"); return; }
            Instant now = clock.instant();
            if ("CREATE".equals(command.type())) {
                int changed = db.jdbc.sql("UPDATE security.usuario SET cd_elegibilidade='BOUND',dt_alteracao=:now "
                        + "WHERE id_usuario=:user AND nr_versao_identidade=:version AND cd_elegibilidade='PASSWORD_PROVISIONING' AND dt_fim IS NULL")
                        .param("user", command.user()).param("version", command.version()).param("now", SecurityJdbcSupport.time(now)).update();
                if (changed != 1) { throw new IllegalStateException("CREATE_CAS_FAILED"); }
                db.jdbc.sql("INSERT INTO security.usuario_identidade(id_usuario_identidade,id_usuario,id_provedor_identidade,cd_emissor,"
                        + "cd_sujeito_externo,cd_email_identidade,dt_inicio) SELECT :id,:user,id_provedor_identidade,:issuer,:uid,:email,:now "
                        + "FROM security.provedor_identidade WHERE cd_projeto=:project AND cd_emissor=:issuer")
                        .param("id", UUID.randomUUID()).param("user", command.user()).param("project", command.project())
                        .param("issuer", command.issuer()).param("uid", command.uid()).param("email", created.email())
                        .param("now", SecurityJdbcSupport.time(now)).update();
            }
            if ("ENABLE".equals(command.type())) {
                int changed = db.jdbc.sql("UPDATE security.usuario SET dt_inicio=:now,dt_fim=NULL,dt_alteracao=:now "
                        + "WHERE id_usuario=:user AND nr_versao_identidade=:version AND cd_elegibilidade='BOUND' AND dt_fim IS NOT NULL")
                        .param("user", command.user()).param("version", command.version()).param("now", SecurityJdbcSupport.time(now)).update();
                if (changed != 1) { throw new IllegalStateException("ENABLE_CAS_FAILED"); }
            }
            finishInside(command, "ACKED", "APPLIED");
            db.event("IDENTITY_COMMAND_ACKED", command.user(), null, now, SecurityEventSink.TargetType.IDENTITY_COMMAND,
                    command.id(), command.user(), null, command.type());
        });
    }
    private void finish(Command command, String state, String result) {
        db.tx.executeWithoutResult(status -> finishInside(command, state, result));
    }
    private void finishInside(Command command, String state, String result) {
        db.jdbc.sql("UPDATE security.comando_identidade SET cd_estado=:state,cd_resultado=:result,dt_alteracao=:now,dt_lease=NULL, "
                + "dt_proxima_tentativa=CASE WHEN :state='RETRY' THEN CAST(:now AS timestamptz) + make_interval(secs => LEAST(3600, "
                + "power(2,LEAST(nr_tentativas,12))::integer)) ELSE dt_proxima_tentativa END "
                + "WHERE id_comando=:id AND cd_claim=:claim AND cd_estado='CLAIMED' AND dt_lease>:now")
                .param("id", command.id()).param("claim", command.claim()).param("state", state).param("result", result)
                .param("now", SecurityJdbcSupport.time(clock.instant())).update();
    }
    private static void lock(Connection session, UUID user, boolean acquire) throws SQLException {
        try (var statement = session.prepareStatement("SELECT pg_advisory_" + (acquire ? "lock" : "unlock") + "(hashtextextended(?,0))")) {
            statement.setString(1, "firebase-user:" + user);
            try (var result = statement.executeQuery()) {
                if (!result.next() || (!acquire && !result.getBoolean(1))) { throw new SQLException("ADVISORY_LOCK_FAILED"); }
            }
        }
    }
    private record Command(UUID id, UUID user, String project, String issuer, String uid, String type, long version, UUID claim) { }
    private record Account(String email, String name) { }
}
