package br.com.gems.firebase.auth;

/** Operações administrativas por UID; nenhum tipo Firebase atravessa esta porta. */
public interface FirebaseAdminGateway {
    /** Snapshot informativo da identidade remota. */
    record User(String uid, String email, String displayName, boolean disabled, boolean emailVerified) { }
    /** Consulta por UID, jamais por e-mail. */
    User getUser(String uid);
    /** Cria o UID planejado, sem receber ou armazenar senha. */
    User createUser(String uid, String email, String displayName);
    /** Suspende/reativa a identidade remota. */
    void setDisabled(String uid, boolean disabled);
    /** Revoga sessões remotas. */
    void revokeRefreshTokens(String uid);
    /** Gera link, sem enviá-lo; o chamador não deve persistir ou registrar o resultado. */
    String passwordResetLink(String email);
}
