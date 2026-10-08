package br.com.gems.firebase.auth;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Snapshot de própria conta/contexto produzido na mesma resolução autenticada. */
public final class AccountViews {
    private AccountViews() { }
    public record Tenant(UUID id, String alias, String name) { }
    public record Container(UUID id, String code, String name) { }
    public record Me(FirebasePrincipal user, List<Tenant> tenants, Set<String> globalActions) {
        public Me { tenants = List.copyOf(tenants); globalActions = Set.copyOf(globalActions); }
    }
    public record Context(Tenant tenant, List<Container> profiles, List<Container> groups, Set<String> actions) {
        public Context { profiles = List.copyOf(profiles); groups = List.copyOf(groups); actions = Set.copyOf(actions); }
    }
}
