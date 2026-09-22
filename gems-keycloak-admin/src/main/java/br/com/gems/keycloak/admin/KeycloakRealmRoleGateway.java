package br.com.gems.keycloak.admin;

import java.util.Set;

/** Operações idempotentes sobre papéis do realm do Keycloak. */
public interface KeycloakRealmRoleGateway {

    /** Garante o papel simples e informa se ele foi criado nesta chamada. */
    boolean ensureRealmRole( String name, String description );

    /** Garante os filhos diretos da composição e devolve quantos foram acrescentados. */
    int ensureCompositeRealmRole( String name, String description, Set<String> directChildren );

}
