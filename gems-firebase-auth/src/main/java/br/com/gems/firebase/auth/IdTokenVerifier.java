package br.com.gems.firebase.auth;

/** Porta substituível para verificar identidade Firebase. */
@FunctionalInterface
public interface IdTokenVerifier {
    /** Verifica token sem expô-lo no resultado ou em erros. */
    VerifiedIdentity verify(String idToken);
}
