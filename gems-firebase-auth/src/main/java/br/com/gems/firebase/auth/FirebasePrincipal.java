package br.com.gems.firebase.auth;

import java.util.Objects;
import java.util.UUID;

/** Usuário local autenticado, sem dados secretos. */
public record FirebasePrincipal(UUID userId, String uid, String name, String email) {
    public FirebasePrincipal { Objects.requireNonNull(userId); Objects.requireNonNull(uid); }
}
