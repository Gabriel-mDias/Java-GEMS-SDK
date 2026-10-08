package br.com.gems.firebase.auth;

import java.time.Instant;
import java.util.UUID;

/** Eventos sanitizados para integração; o JDBC mantém também a trilha durável. */
@FunctionalInterface
public interface SecurityEventSink {
    /** Emite somente identificadores estáveis; nunca payloads remotos. */
    void emit(Event event);
    /** Evento público sem credencial ou token. */
    record Event(UUID id, String type, UUID userId, UUID tenantId, Instant occurredAt,
            UUID actorUserId, TargetType targetType, UUID targetId, UUID sourceId, UUID relatedId,
            String action, UUID correlationId) {
        public Event(UUID id, String type, UUID userId, UUID tenantId, Instant occurredAt) {
            this(id, type, userId, tenantId, occurredAt, null,
                    userId != null ? TargetType.USER : TargetType.TENANT, userId != null ? userId : tenantId,
                    null, null, null, null);
        }
    }
    /** Tipos fechados de objetos auditados; os IDs permitem reconstruir as relações. */
    enum TargetType {
        USER, TENANT, MEMBERSHIP, PROFILE, GROUP, MEMBER_ACTION, PROFILE_ACTION, GROUP_ACTION,
        GLOBAL_ACTION, MEMBER_PROFILE, GROUP_MEMBER, GROUP_PROFILE, IDENTITY_COMMAND, AUTHENTICATION
    }
}
