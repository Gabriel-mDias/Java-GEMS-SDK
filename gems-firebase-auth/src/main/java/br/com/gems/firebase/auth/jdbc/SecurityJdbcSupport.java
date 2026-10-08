package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.SecurityEventSink;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Infraestrutura interna compartilhada pelos serviços JDBC qualificados. */
final class SecurityJdbcSupport {
    final JdbcClient jdbc;
    final TransactionTemplate tx;
    final SecurityEventSink events;
    final br.com.gems.firebase.auth.SecurityActorProvider actors;
    SecurityJdbcSupport(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events) {
        this(jdbc, tx, events, new br.com.gems.firebase.auth.SpringSecurityActorProvider());
    }
    SecurityJdbcSupport(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events,
            br.com.gems.firebase.auth.SecurityActorProvider actors) {
        this.jdbc = jdbc; this.tx = tx; this.events = events; this.actors = actors;
    }
    static Timestamp time(Instant instant) { return Timestamp.from(instant); }
    void event(String type, UUID user, UUID tenant, Instant now) {
        event(type, user, tenant, now, user != null ? SecurityEventSink.TargetType.USER : SecurityEventSink.TargetType.TENANT,
                user != null ? user : tenant, null, null, null);
    }
    void event(String type, UUID user, UUID tenant, Instant now, SecurityEventSink.TargetType targetType,
            UUID target, UUID source, UUID related, String action) {
        UUID id = UUID.randomUUID();
        UUID actor = null;
        boolean bootstrap = false;
        try { var current = actors.currentActor(); actor = current.userId(); bootstrap = current.bootstrap(); }
        catch (br.com.gems.firebase.auth.FirebaseAuthException absent) { /* processo técnico/identidade ainda não resolvida */ }
        String eventType = bootstrap ? "BOOTSTRAP_" + type : type;
        jdbc.sql("INSERT INTO security.evento_seguranca(id_evento,cd_evento,id_usuario,id_tenant,dt_evento,"
                + "id_ator,cd_tipo_alvo,id_alvo,id_origem,id_relacionado,cd_acao) "
                + "VALUES(:id,:type,:user,:tenant,:now,:actor,:targetType,:target,:source,:related,:action)")
                .param("id", id).param("type", eventType).param("actor", actor).param("targetType", targetType.name())
                .param("target", target).param("source", source).param("related", related).param("action", action)
                .param("user", user).param("tenant", tenant).param("now", time(now)).update();
        jdbc.sql("INSERT INTO security.entrega_evento(id_evento,cd_estado,dt_proxima_tentativa) VALUES(:id,'PENDING',:now)")
                .param("id", id).param("now", time(now)).update();
        var event = new SecurityEventSink.Event(id, eventType, user, tenant, now, actor, targetType, target, source, related, action, null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                // A falha de entrega não pode desfazer nem disfarçar um commit de segurança.
                // O consumidor pode repetir a publicação pelo ID da outbox append-only.
                try { events.emit(event); } catch (RuntimeException failure) { /* durable replay */ }
            }
        });
    }
}
