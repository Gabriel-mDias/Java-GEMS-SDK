package br.com.gems.firebase.auth.jdbc;

import br.com.gems.firebase.auth.DelegationPolicy;
import br.com.gems.firebase.auth.SecurityEventSink;
import br.com.gems.security.authorization.AuthorizationCatalog;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Sincroniza apenas ações conhecidas; preserva histórico e recusa mudança de escopo. */
public final class JdbcSecurityCatalog {
    private final SecurityJdbcSupport db;
    private final AuthorizationCatalog catalog;
    private final DelegationPolicy delegation;
    private final Clock clock;
    private final String project;
    public JdbcSecurityCatalog(JdbcClient jdbc, TransactionTemplate tx, SecurityEventSink events,
            AuthorizationCatalog catalog, DelegationPolicy delegation, Clock clock, String project) {
        db = new SecurityJdbcSupport(jdbc, tx, events);
        this.catalog = catalog; this.delegation = delegation; this.clock = clock; this.project = project;
    }
    /** Executada somente com persistence opt-in e migrations já aplicadas pelo consumidor. */
    public void initialize() {
        if (!catalog.tenantActions().containsAll(delegation.delegableTenantActions())) {
            throw new IllegalArgumentException("Delegabilidade fora do catálogo tenant");
        }
        db.tx.executeWithoutResult(status -> {
            var now = clock.instant();
            db.jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended('gems-firebase-catalog',0))").query().listOfRows();
            var projects = db.jdbc.sql("SELECT cd_projeto FROM security.provedor_identidade FOR UPDATE")
                    .query(String.class).list();
            if (projects.size() > 1 || projects.stream().anyMatch(value -> !value.equals(project))) {
                throw new IllegalStateException("Projeto Firebase incompatível com o banco");
            }
            if (projects.isEmpty()) {
                db.jdbc.sql("INSERT INTO security.provedor_identidade(id_provedor_identidade,cd_provedor,cd_projeto,cd_emissor,dt_inicio) "
                        + "VALUES(:id,'FIREBASE',:project,:issuer,:now)").param("id", UUID.randomUUID())
                        .param("project", project).param("issuer", "https://securetoken.google.com/" + project)
                        .param("now", SecurityJdbcSupport.time(now)).update();
            }
            var existing = db.jdbc.sql("SELECT cd_acao,cd_escopo FROM security.role_seguranca FOR UPDATE")
                    .query((rs, row) -> new Entry(rs.getString(1), rs.getString(2))).list();
            Set<String> known = new HashSet<>();
            for (Entry entry : existing) {
                String expected = catalog.globalActions().contains(entry.action()) ? "GLOBAL"
                        : catalog.tenantActions().contains(entry.action()) ? "TENANT" : null;
                if (expected != null && !expected.equals(entry.scope())) {
                    throw new IllegalStateException("Escopo do catálogo incompatível com o banco");
                }
                if (expected == null) { db.event("AUTHORIZATION_CATALOG_DRIFT", null, null, now); }
                known.add(entry.action());
            }
            synchronize(catalog.globalActions(), "GLOBAL", known, now);
            synchronize(catalog.tenantActions(), "TENANT", known, now);
        });
    }
    private void synchronize(Set<String> actions, String scope, Set<String> existing, java.time.Instant now) {
        for (String action : actions) {
            boolean delegable = delegation.delegableTenantActions().contains(action);
            if (!existing.contains(action)) {
                db.jdbc.sql("INSERT INTO security.role_seguranca(id_role,cd_acao,cd_escopo,fl_delegavel,dt_inicio) "
                        + "VALUES(:id,:action,:scope,:delegable,:now)").param("id", UUID.randomUUID())
                        .param("action", action).param("scope", scope).param("delegable", delegable)
                        .param("now", SecurityJdbcSupport.time(now)).update();
            } else {
                db.jdbc.sql("UPDATE security.role_seguranca SET fl_delegavel=:delegable WHERE cd_acao=:action")
                        .param("delegable", delegable).param("action", action).update();
            }
        }
    }
    private record Entry(String action, String scope) { }
}
