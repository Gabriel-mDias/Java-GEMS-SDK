package br.com.gems.tenant;

/** Permite bootstrap do Spring Data sem instalar tenant fictício; SQL empresarial continua recusado. */
public final class BootstrapTenantIdentifierResolver extends TenantIdentifierResolver {
    /** Contém caracteres inválidos como alias, inclusive para o sanitizer legado. */
    public static final String MISSING_CONTEXT = "[tenant-context-required]";
    @Override public String resolveCurrentTenantIdentifier() {
        return JpaTenantContext.current().isEmpty() ? MISSING_CONTEXT : super.resolveCurrentTenantIdentifier();
    }
}
