package br.com.gems.firebase.auth;

import java.time.Instant;
import java.util.Objects;

/** Identidade verificada; não contém token, claims arbitrárias nem permissões. */
public record VerifiedIdentity(String projectId, String issuer, String uid, String email,
        boolean emailVerified, String signInProvider, String displayName, Instant authenticatedAt) {
    public VerifiedIdentity {
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(issuer);
        if (!issuer.equals("https://securetoken.google.com/" + projectId) || uid == null || uid.isBlank()
                || uid.length() > 128) {
            throw new FirebaseAuthException(FirebaseAuthException.Reason.INVALID_TOKEN);
        }
        Objects.requireNonNull(authenticatedAt);
    }
}
