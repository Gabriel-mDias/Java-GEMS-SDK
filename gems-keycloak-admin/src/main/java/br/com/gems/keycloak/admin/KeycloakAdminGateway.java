package br.com.gems.keycloak.admin;

import java.util.List;
import java.util.Optional;

/**
 * As operações administrativas sobre o provedor de identidade, na linguagem de quem as chama.
 * <p>
 * A interface existe separada do transporte para que o consumidor dependa da <em>operação</em>
 * ("garantir a organização", "criar a conta") e não do REST do Keycloak. É o que permite trocar o
 * provedor, ou dublar a integração em teste, sem tocar em regra de negócio.
 * </p>
 * <p>
 * <strong>Toda falha sai como {@link KeycloakAdminException}</strong> — e portanto como 502 no
 * envelope de {@code gems-exception} (KA-1).
 * </p>
 */
public interface KeycloakAdminGateway {

    /** A conta como este módulo a expõe, sem os campos de representação do provedor. */
    record User(String id, String name, String username, String email, boolean enabled) {}

    /** Devolve o identificador da organização, criando-a se ainda não existir. */
    String ensureOrganization( String alias, String displayName );

    /** Altera o nome de exibição preservando alias e situação. */
    void updateOrganization( String organizationId, String displayName );

    Optional<User> findUserByEmail( String email );

    User createUser( String name, String username, String email, String password );

    /**
     * Devolve o identificador do grupo da organização, criando-o se ainda não existir.
     * <p>
     * O nome do grupo é <strong>parâmetro</strong>. A origem desta operação no Meduc tinha o grupo
     * {@code GESTOR} embutido no corpo do método; um nome de papel de um projeto específico dentro de
     * uma biblioteca só serve àquele projeto, e obriga todos os outros a contorná-lo.
     * </p>
     */
    String ensureGroup( String organizationId, String groupName );

    /** Mapeia um papel do realm sobre o grupo da organização, se ainda não estiver mapeado. */
    void ensureRealmRoleOnGroup( String organizationId, String groupId, String role );

    void addOrganizationMember( String organizationId, String userId );

    void addGroupMember( String organizationId, String groupId, String userId );

    void removeGroupMember( String organizationId, String groupId, String userId );

    List<User> listGroupMembers( String organizationId, String groupId, String search, int first, int max );

}
