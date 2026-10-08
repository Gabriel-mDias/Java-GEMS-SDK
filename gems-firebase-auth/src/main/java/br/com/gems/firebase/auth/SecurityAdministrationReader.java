package br.com.gems.firebase.auth;

import java.util.Optional;
import java.util.UUID;
import br.com.gems.firebase.auth.AdministrationViews.*;

/** Consultas administrativas tipadas; o backend autoriza as ações concretas e a SDK exige scope do ator. */
public interface SecurityAdministrationReader {
    Optional<User> findUser(UUID id);
    Page<User> listUsers(Query query);
    Optional<Tenant> findTenant(UUID id);
    Page<Tenant> listTenants(Query query);
    Optional<Profile> findProfile(UUID tenant, UUID id);
    Page<Profile> listProfiles(UUID tenant, Query query);
    Optional<Group> findGroup(UUID tenant, UUID id);
    Page<Group> listGroups(UUID tenant, Query query);
    Optional<Membership> findMembership(UUID tenant, UUID id);
    Page<Membership> listMemberships(UUID tenant, Query query);
    Optional<Grant> findGrant(UUID tenant, UUID id);
    Page<Grant> listGrants(UUID tenant, Query query);
    Page<Grant> listGlobalGrants(UUID user, Query query);
    Optional<Grant> findGlobalGrant(UUID user, UUID id);
}
