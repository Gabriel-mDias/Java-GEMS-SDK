package br.com.gems.tenant;

/** Naming aditivo escolhido somente por gems.tenant.strict-alias=true. */
public final class StrictTenantSchemaNaming extends TenantSchemaNaming {
    private final StrictTenantAliasValidator validator;
    public StrictTenantSchemaNaming(String prefix, String globalSchema) {
        super(prefix, globalSchema); validator = new StrictTenantAliasValidator(prefix);
    }
    @Override public String schemaFor(String alias) { return schemaPrefix() + validator.validate(alias); }
}
