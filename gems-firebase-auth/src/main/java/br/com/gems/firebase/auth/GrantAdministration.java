package br.com.gems.firebase.auth;

import java.util.UUID;

/** Concessões próprias sem enums JDBC; todas as heranças passam pela política de delegação. */
public interface GrantAdministration {
    enum Target { MEMBER, PROFILE, GROUP }
    enum Link { MEMBER_PROFILE, GROUP_MEMBER, GROUP_PROFILE }
    UUID createProfile(UUID tenant, String code, String name);
    UUID createGroup(UUID tenant, String code, String name);
    void updateProfile(UUID tenant, UUID id, String name, String description);
    void updateGroup(UUID tenant, UUID id, String name, String description);
    void suspendProfile(UUID tenant, UUID id);
    void suspendGroup(UUID tenant, UUID id);
    UUID grant(Target target, UUID tenant, UUID owner, String action);
    UUID link(Link link, UUID tenant, UUID left, UUID right);
    void revoke(Target target, UUID tenant, UUID id);
    void revoke(Link link, UUID tenant, UUID id);
    UUID grantGlobal(UUID user, String action);
    void revokeGlobal(UUID user, UUID grant);
}
