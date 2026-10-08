package br.com.gems.firebase.auth;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Contrato ASCII de alias para schemas PostgreSQL tenant_ de até 63 bytes. */
public final class StrictTenantAliasValidator implements TenantAliasValidator {
    private static final Pattern ASCII = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,55}");
    private static final Set<String> RESERVED = Set.of("public", "security", "administracao", "auditoria",
            "modelo", "information_schema", "global", "__global__", "tenant_context_required");
    @Override
    public String validate(String alias) {
        if (alias == null || !ASCII.matcher(alias).matches()) { throw FirebaseAuthException.denied(); }
        String normalized = alias.toLowerCase(Locale.ROOT);
        if (RESERVED.contains(normalized) || normalized.startsWith("pg_") || normalized.startsWith("tenant_")) {
            throw FirebaseAuthException.denied();
        }
        return normalized;
    }
}
