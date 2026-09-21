# Contrato público — gems-keycloak-admin 3.2.0

`KeycloakAdminGateway` permanece inalterado. O cliente REST implementa também:

```java
public interface KeycloakUserLifecycleGateway {
    Optional<KeycloakUserSnapshot> findUserById(String userId);
    KeycloakUserSnapshot snapshotUser(String userId);
    void updateUser(KeycloakUserSnapshot user);
    void setUserEnabled(String userId, boolean enabled);
    void deleteUser(String userId);
    Set<String> listUserGroupIds(String userId);
    void joinRealmGroup(String userId, String groupId);
    void leaveRealmGroup(String userId, String groupId);
    void restoreUser(KeycloakUserSnapshot snapshot);
}

public record KeycloakUserSnapshot(
    String id, String firstName, String lastName, String username, String email,
    boolean enabled, Map<String, List<String>> attributes, Set<String> groupIds) {}

public interface KeycloakRealmRoleGateway {
    boolean ensureRealmRole(String name, String description);
    int ensureCompositeRealmRole(String name, String description, Set<String> directChildren);
}
```

## Semântica

- `createUser` mantém assinatura e passa a remover a conta se reset de senha falhar.
- `snapshotUser` falha se ausente; `findUserById` distingue ausência.
- `restoreUser` restaura dados, enabled e pertencimento a grupos do snapshot; não recria id apagado.
- join/leave são idempotentes.
- role existente retorna `false`; conflito concorrente é sucesso idempotente.
- composta retorna número de filhos acrescentados, preserva extras e consulta filhos diretos.
- toda falha externa sai como `KeycloakAdminException`; corpo do provedor não chega à mensagem.
