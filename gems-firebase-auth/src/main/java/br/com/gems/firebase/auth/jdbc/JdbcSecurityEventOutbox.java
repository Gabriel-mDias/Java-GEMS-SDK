package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.SecurityEventSink;
import br.com.gems.firebase.auth.SecurityEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Entrega durável da trilha append-only; consumidor deduplica por UUID. */
public final class JdbcSecurityEventOutbox {
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    public JdbcSecurityEventOutbox(JdbcClient jdbc, TransactionTemplate tx, Clock clock) { this.jdbc = jdbc; this.tx = tx; this.clock = clock; }
    /** Entrega pelo ID com lease/CAS; falha e restart repetem sem depender de ordem de commit. */
    public boolean publishNext(SecurityEventPublisher publisher) {
        UUID claim = UUID.randomUUID();
        SecurityEventSink.Event event = tx.execute(status -> {
            var ids = jdbc.sql("SELECT id_evento FROM security.entrega_evento WHERE (cd_estado='PENDING' AND dt_proxima_tentativa<=:now) "
                    + "OR (cd_estado='CLAIMED' AND dt_lease<=:now) ORDER BY dt_proxima_tentativa,id_evento LIMIT 1 FOR UPDATE SKIP LOCKED")
                    .param("now", SecurityJdbcSupport.time(clock.instant())).query(UUID.class).list();
            if (ids.isEmpty()) { return null; }
            UUID id = ids.getFirst();
            jdbc.sql("UPDATE security.entrega_evento SET cd_estado='CLAIMED',cd_claim=:claim,dt_lease=:lease,nr_tentativas=nr_tentativas+1 WHERE id_evento=:id")
                    .param("id", id).param("claim", claim).param("lease", SecurityJdbcSupport.time(clock.instant().plusSeconds(120))).update();
            return jdbc.sql("SELECT " + EVENT_COLUMNS + " FROM security.evento_seguranca WHERE id_evento=:id")
                    .param("id", id).query(JdbcSecurityEventOutbox::event).single();
        });
        if (event == null) { return false; }
        String outcome;
        try { publisher.emit(event); outcome = "ACKED"; }
        catch (RuntimeException failure) { outcome = "PENDING"; }
        String result = outcome;
        tx.executeWithoutResult(status -> jdbc.sql("UPDATE security.entrega_evento SET cd_estado=:state,dt_lease=NULL, "
                + "dt_proxima_tentativa=CASE WHEN :state='PENDING' THEN CAST(:now AS timestamptz) + make_interval(secs => LEAST(3600, "
                + "power(2,LEAST(nr_tentativas,12))::integer)) ELSE dt_proxima_tentativa END "
                + "WHERE id_evento=:id AND cd_claim=:claim AND cd_estado='CLAIMED' AND dt_lease>:now")
                .param("id", event.id()).param("claim", claim).param("state", result).param("now", SecurityJdbcSupport.time(clock.instant())).update());
        return true;
    }
    /** Consulta histórica paginada; entrega concorrente confiável deve usar publishNext. */
    public List<SecurityEventSink.Event> readAfter(Instant timestamp, UUID id, int limit) {
        if (limit < 1 || limit > 1000) { throw new IllegalArgumentException("Limite inválido"); }
        return jdbc.sql("SELECT " + EVENT_COLUMNS + " FROM security.evento_seguranca "
                + "WHERE (dt_evento,id_evento)>(:time,:id) ORDER BY dt_evento,id_evento LIMIT :limit")
                .param("time", SecurityJdbcSupport.time(timestamp)).param("id", id).param("limit", limit)
                .query(JdbcSecurityEventOutbox::event).list();
    }
    private static final String EVENT_COLUMNS = "id_evento,cd_evento,id_usuario,id_tenant,dt_evento,id_ator,cd_tipo_alvo,id_alvo,id_origem,id_relacionado,cd_acao,id_correlacao";
    private static SecurityEventSink.Event event(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new SecurityEventSink.Event(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                rs.getTimestamp(5).toInstant(), rs.getObject(6, UUID.class), SecurityEventSink.TargetType.valueOf(rs.getString(7)),
                rs.getObject(8, UUID.class), rs.getObject(9, UUID.class), rs.getObject(10, UUID.class), rs.getString(11), rs.getObject(12, UUID.class));
    }
}
