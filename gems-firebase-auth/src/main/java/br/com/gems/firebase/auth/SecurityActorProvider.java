package br.com.gems.firebase.auth;

import java.util.UUID;

/** Identifica o ator no backend; jamais receber esta porta ou seu resultado de um DTO HTTP. */
@FunctionalInterface
public interface SecurityActorProvider {
    /** Retorna identidade confiável; ações são recalculadas no SQL pela SDK. */
    Actor currentActor();
    /** Bootstrap é uma capacidade de configuração explícita exclusiva de CLI/job central auditado. */
    record Actor(UUID userId, boolean bootstrap, java.util.Optional<String> tenantAlias) {
        public Actor { java.util.Objects.requireNonNull(userId, "Ator obrigatório"); tenantAlias = java.util.Objects.requireNonNull(tenantAlias); }
        public Actor(UUID userId, boolean bootstrap) { this(userId, bootstrap, java.util.Optional.empty()); }
    }
    /** Capacidade dedicada, não registrada automaticamente nem inferida de login/role. */
    static SecurityActorProvider trustedBootstrap(UUID operatorId) {
        Actor operator = new Actor(operatorId, true);
        return () -> operator;
    }
}
