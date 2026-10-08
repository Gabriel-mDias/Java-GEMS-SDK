package br.com.gems.firebase.auth;

/** Falha pública sanitizada, sem causa ou payload do provedor. */
public final class FirebaseAuthException extends RuntimeException {
    /** Classes de falha com semântica HTTP estável. */
    public enum Reason {
        INVALID_TOKEN(401), LOCAL_DENIED(403), PROVIDER_UNAVAILABLE(503);
        private final int status;
        Reason(int status) { this.status = status; }
        /** Status HTTP da falha. */
        public int status() { return status; }
    }
    private final Reason reason;
    public FirebaseAuthException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }
    /** Razão estável; nunca inclui dados de credenciais. */
    public Reason reason() { return reason; }
    /** Recusa local. */
    public static FirebaseAuthException denied() { return new FirebaseAuthException(Reason.LOCAL_DENIED); }
}
