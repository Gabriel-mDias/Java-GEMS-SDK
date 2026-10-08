package br.com.gems.firebase.auth;

import java.util.Set;
import java.util.UUID;

/** Cadastro central e memberships delimitadas pelo tenant do ator. */
public interface TenantAdministration {
    UUID create(String alias, String name);
    void rename(UUID tenant, String name);
    void suspend(UUID tenant);
    UUID addMember(UUID tenant, UUID user);
    UUID addMember(UUID tenant, UUID user, String selfAction);
    void endMembership(UUID tenant, UUID membership);
    Set<String> expectedSchemas();
}
