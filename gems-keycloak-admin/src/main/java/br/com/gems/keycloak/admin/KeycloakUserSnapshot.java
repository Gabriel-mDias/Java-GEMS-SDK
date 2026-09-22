package br.com.gems.keycloak.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Retrato imutável de uma conta do Keycloak necessário para restaurar seu estado.
 *
 * @param id identificador estável da conta no provedor.
 * @param firstName primeiro nome da conta.
 * @param lastName sobrenome da conta.
 * @param username nome de acesso.
 * @param email endereço de e-mail.
 * @param enabled situação de acesso da conta.
 * @param attributes atributos declarados no provedor.
 * @param groupIds identificadores dos grupos de que a conta participa.
 */
public record KeycloakUserSnapshot(String id, String firstName, String lastName, String username,
        String email, boolean enabled, Map<String, List<String>> attributes, Set<String> groupIds) {

    /** Cria o retrato validando os campos obrigatórios e isolando as coleções do chamador. */
    public KeycloakUserSnapshot {
        id = Objects.requireNonNull( id, "id" );
        firstName = Objects.requireNonNull( firstName, "firstName" );
        lastName = Objects.requireNonNull( lastName, "lastName" );
        username = Objects.requireNonNull( username, "username" );
        email = Objects.requireNonNull( email, "email" );
        attributes = immutableAttributes( attributes );
        groupIds = Set.copyOf( Objects.requireNonNull( groupIds, "groupIds" ) );
    }

    private static Map<String, List<String>> immutableAttributes( Map<String, List<String>> attributes ) {
        Objects.requireNonNull( attributes, "attributes" );
        var copy = new LinkedHashMap<String, List<String>>();
        attributes.forEach( ( key, values ) -> copy.put( Objects.requireNonNull( key, "attribute key" ),
                List.copyOf( Objects.requireNonNull( values, "attribute values" ) ) ) );
        return Map.copyOf( copy );
    }

}
