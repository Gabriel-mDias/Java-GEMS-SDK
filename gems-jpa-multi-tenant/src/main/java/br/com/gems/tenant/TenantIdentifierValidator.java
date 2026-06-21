package br.com.gems.tenant;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

/**
 * Validação centralizada de identificadores de tenant (siglas/acrônimos).
 * <p>
 * Como o identificador do tenant é usado para compor dinamicamente o nome do schema
 * em comandos SQL ({@code CREATE SCHEMA ...} / {@code SET SCHEMA ...}), ele precisa ser
 * estritamente validado para evitar injeção de SQL. Identificadores válidos contêm apenas
 * letras minúsculas, dígitos e underscore.
 * </p>
 */
@UtilityClass
public class TenantIdentifierValidator {

    private static final Pattern SAFE_TENANT = Pattern.compile("^[a-z0-9_]+$");

    /**
     * Normaliza (trim + lowercase) e valida o identificador do tenant.
     *
     * @param tenant identificador bruto (sigla/acrônimo) do tenant.
     * @return o identificador normalizado e validado.
     * @throws IllegalArgumentException se o identificador for nulo, vazio ou contiver caracteres não permitidos.
     */
    public static String sanitize(String tenant) {
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException("Tenant identifier must not be null or blank.");
        }

        String normalized = tenant.trim().toLowerCase();
        if (!SAFE_TENANT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid tenant identifier: " + tenant);
        }

        return normalized;
    }
}
