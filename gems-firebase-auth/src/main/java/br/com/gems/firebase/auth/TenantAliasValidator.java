package br.com.gems.firebase.auth;

/** Validação de alias independente do motor JPA. */
@FunctionalInterface
public interface TenantAliasValidator {
    /** Normaliza e valida; nenhum alias bruto deve compor SQL. */
    String validate(String alias);
}
