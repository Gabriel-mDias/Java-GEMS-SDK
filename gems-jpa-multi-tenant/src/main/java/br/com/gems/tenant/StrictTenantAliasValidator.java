package br.com.gems.tenant;

import java.util.Locale;
import java.util.Set;

/** Validação opt-in ASCII, independente da normalização legada de TenantIdentifierValidator. */
public final class StrictTenantAliasValidator {
    private static final Set<String> RESERVED = Set.of("public", "security", "administracao", "auditoria",
            "modelo", "information_schema", "global", "tenant_context_required");
    private final String prefix;
    public StrictTenantAliasValidator(String prefix) {
        if (prefix == null || !prefix.matches("[a-z][a-z0-9_]*") || prefix.length() >= 63) {
            throw new IllegalArgumentException("Prefixo de schema inválido");
        }
        this.prefix = prefix;
    }
    /** Recusa espaços, Unicode, reservados e truncamento do identificador PostgreSQL. */
    public String validate(String alias) {
        if (alias == null || !alias.matches("[A-Za-z][A-Za-z0-9_]*")) { throw new IllegalArgumentException("Alias inválido"); }
        String normalized = alias.toLowerCase(Locale.ROOT);
        if (prefix.length() + normalized.length() > 63 || RESERVED.contains(normalized)
                || normalized.startsWith("pg_") || normalized.startsWith("tenant_")) {
            throw new IllegalArgumentException("Alias reservado ou demasiado longo");
        }
        return normalized;
    }
}
