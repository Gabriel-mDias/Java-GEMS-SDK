package br.com.gems.firebase.auth;

import java.util.Set;

/** Porta do catálogo delegável do consumidor, separada de ações centrais globais. */
@FunctionalInterface
public interface DelegationPolicy {
    /** Ações de tenant que o produto permite delegar. */
    Set<String> delegableTenantActions();
}
