package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.FirebaseAuthException;
import br.com.gems.firebase.auth.SecurityEventSink;
import br.com.gems.firebase.auth.TenantAliasValidator;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Provisionamento explícito fora de HTTP, com lock PostgreSQL pinado à sessão. */
public final class JdbcTenantProvisioner {
    /** Adaptador de migrations do consumidor; a revisão deve ser lida do histórico aplicado. */
    public interface Migration {
        /** Aplica migrations usando a sessão fornecida sem fechá-la. */
        void apply(Connection connection, String schema) throws Exception;
        /** Consulta a revisão realmente aplicada, após a migração. */
        long appliedRevision(Connection connection, String schema) throws Exception;
    }
    private final DataSource dataSource;
    private final SecurityJdbcSupport db;
    private final TenantAliasValidator aliases;
    private final Clock clock;
    public JdbcTenantProvisioner(DataSource dataSource, JdbcClient jdbc, TransactionTemplate tx,
            SecurityEventSink events, TenantAliasValidator aliases, Clock clock) {
        this.dataSource = dataSource; db = new SecurityJdbcSupport(jdbc, tx, events); this.aliases = aliases; this.clock = clock;
    }
    /** Cria, migra, verifica revisão e só então registra READY; retry conserva alias/histórico. */
    public void provision(UUID tenant, long requiredRevision, Migration migration) {
        if (requiredRevision < 0) { throw new IllegalArgumentException("Revisão inválida"); }
        String alias = aliases.validate(db.jdbc.sql("SELECT cd_alias FROM security.tenant WHERE id_tenant=:tenant")
                .param("tenant", tenant).query(String.class).optional().orElseThrow(FirebaseAuthException::denied));
        String schema = "tenant_" + alias;
        try (Connection session = dataSource.getConnection()) {
            String original = session.getSchema(); boolean locked = false;
            try {
                lock(session, alias, true); locked = true;
                metadata(tenant, "PENDING", 0, null);
                try (var statement = session.createStatement()) { statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema); }
                migration.apply(session, schema);
                long actual = migration.appliedRevision(session, schema);
                if (actual < requiredRevision) { throw new IllegalStateException("MIGRATION_REVISION_INCOMPLETE"); }
                metadata(tenant, "READY", actual, null);
            } catch (Exception failure) {
                metadata(tenant, "FAILED", 0, "MIGRATION_FAILED");
                throw new IllegalStateException("TENANT_PROVISIONING_FAILED");
            } finally {
                try {
                    session.setSchema(original);
                    if (locked) { lock(session, alias, false); }
                } catch (SQLException failure) {
                    session.abort(Runnable::run);
                    throw failure;
                }
            }
        } catch (SQLException failure) { throw new IllegalStateException("TENANT_PROVISIONING_SESSION_FAILED"); }
    }
    private void metadata(UUID tenant, String state, long revision, String error) {
        db.tx.executeWithoutResult(status -> {
            var now = clock.instant();
            db.jdbc.sql("UPDATE security.tenant SET cd_provisionamento=:state,nr_revisao_aplicada=:revision,"
                    + "cd_erro_provisionamento=:error,dt_provisionamento=:now WHERE id_tenant=:tenant")
                    .param("tenant", tenant).param("state", state).param("revision", revision).param("error", error)
                    .param("now", SecurityJdbcSupport.time(now)).update();
            db.event("TENANT_PROVISIONING_" + state, null, tenant, now);
        });
    }
    private static void lock(Connection connection, String alias, boolean acquire) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT pg_advisory_" + (acquire ? "lock" : "unlock") + "(hashtextextended(?,0))")) {
            statement.setString(1, "firebase-tenant:" + alias);
            try (var result = statement.executeQuery()) {
                if (!result.next() || (!acquire && !result.getBoolean(1))) { throw new SQLException("PROVISIONING_LOCK_FAILED"); }
            }
        }
    }
}
