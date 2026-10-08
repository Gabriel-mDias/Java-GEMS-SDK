package br.com.gems.firebase.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs próprios da SDK para consultas; não expõem SQL nem tipos Firebase Admin. */
public final class AdministrationViews {
    private AdministrationViews() { }
    public enum Eligibility { GOOGLE_PENDING, PASSWORD_PROVISIONING, BOUND }
    public enum Provisioning { PENDING, FAILED, READY }
    public record User(UUID id, String name, String email, Eligibility eligibility, Instant startsAt, Instant endsAt) { }
    public record Tenant(UUID id, String alias, String name, Provisioning provisioning, long appliedRevision, Instant startsAt, Instant endsAt) { }
    public record Profile(UUID id, UUID tenantId, String code, String name, String description, Instant startsAt, Instant endsAt) { }
    public record Group(UUID id, UUID tenantId, String code, String name, String description, Instant startsAt, Instant endsAt) { }
    public record Membership(UUID id, UUID tenantId, UUID userId, Instant startsAt, Instant endsAt) { }
    public record Grant(UUID id, UUID tenantId, SecurityEventSink.TargetType type, UUID sourceId, UUID relatedId,
            String action, Instant startsAt, Instant endsAt) { }
    /** Cursor UUID ordenado e limite estrito, sem sort/SQL arbitrário do consumidor. */
    public record Query(UUID afterId, int limit, boolean includeHistory) {
        public Query { if (limit < 1 || limit > 1000) { throw new IllegalArgumentException("Limite inválido"); } }
    }
    public record Page<T>(List<T> items, UUID nextCursor) { public Page { items = List.copyOf(items); } }
}
