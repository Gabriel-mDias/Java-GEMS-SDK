package br.com.gems.keycloak.admin;

import java.util.Optional;

/** Operações idempotentes sobre grupos de primeiro nível do realm do Keycloak. */
public interface KeycloakRealmGroupGateway {

    /**
     * Grupo do realm exposto sem tipos específicos do cliente administrativo do provedor.
     *
     * @param id identificador atribuído pelo provedor.
     * @param name nome completo do grupo.
     */
    record RealmGroup(String id, String name) {}

    /**
     * Procura um grupo de primeiro nível pelo nome exato.
     *
     * @param name nome completo do grupo.
     * @return o grupo encontrado ou vazio; nomes nulos ou em branco também resultam em vazio.
     */
    Optional<RealmGroup> findRealmGroupByName( String name );

    /**
     * Cria um grupo de primeiro nível e devolve seu identificador.
     *
     * <p>Uma criação concorrente que responda 409 é tratada como sucesso depois que o grupo
     * vencedor é localizado pelo nome exato.</p>
     *
     * @param name nome completo do grupo.
     * @return identificador atribuído pelo provedor.
     */
    String createRealmGroup( String name );

    /**
     * Garante que o grupo receba a realm role, preservando todos os mapeamentos existentes.
     *
     * @param groupId identificador do grupo.
     * @param role nome exato da realm role.
     * @return {@code true} quando o mapeamento foi acrescentado; {@code false} quando a role já
     *         era efetiva no grupo ou outra instância venceu a mesma operação.
     */
    boolean ensureRealmRoleOnRealmGroup( String groupId, String role );

}
