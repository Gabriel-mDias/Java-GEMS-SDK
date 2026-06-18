package br.com.gems.tenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantIdentifierResolverTest {

    private TenantIdentifierResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TenantIdentifierResolver();
        JpaTenantContext.clear();
    }

    @Test
    void resolveCurrentTenantIdentifier_WhenTenantIsSet_ReturnsTenant() {
        JpaTenantContext.setCurrentTenant("tenant_alpha");
        
        String resolvedTenant = resolver.resolveCurrentTenantIdentifier();
        
        assertEquals("tenant_alpha", resolvedTenant);
    }

    @Test
    void resolveCurrentTenantIdentifier_WhenTenantIsNotSet_ReturnsPublic() {
        String resolvedTenant = resolver.resolveCurrentTenantIdentifier();
        
        assertEquals("public", resolvedTenant);
    }

    @Test
    void validateExistingCurrentSessions_AlwaysReturnsTrue() {
        assertTrue(resolver.validateExistingCurrentSessions());
    }
}
