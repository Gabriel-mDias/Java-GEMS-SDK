package br.com.gems.keycloak.admin;

import java.util.Optional;
import java.util.Set;

/** Operações de ciclo de vida de uma conta sem expor o cliente REST do provedor. */
public interface KeycloakUserLifecycleGateway {

    /** Procura a conta pelo identificador, sem tratar sua ausência como falha. */
    Optional<KeycloakUserSnapshot> findUserById( String userId );

    /** Obtém o retrato da conta; a ausência é uma falha de integração sanitizada. */
    KeycloakUserSnapshot snapshotUser( String userId );

    /** Atualiza os dados escalares e atributos da conta existente. */
    void updateUser( KeycloakUserSnapshot user );

    /** Habilita ou desabilita a conta existente. */
    void setUserEnabled( String userId, boolean enabled );

    /** Exclui a conta existente. */
    void deleteUser( String userId );

    /** Lista os identificadores dos grupos associados à conta. */
    Set<String> listUserGroupIds( String userId );

    /** Associa a conta ao grupo, sem falhar se a associação já existir. */
    void joinRealmGroup( String userId, String groupId );

    /** Remove a conta do grupo, sem falhar se a associação já não existir. */
    void leaveRealmGroup( String userId, String groupId );

    /** Restaura uma conta existente e seus grupos, sem recriar identificador excluído. */
    void restoreUser( KeycloakUserSnapshot snapshot );

}
