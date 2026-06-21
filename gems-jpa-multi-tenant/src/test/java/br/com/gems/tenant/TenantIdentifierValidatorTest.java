package br.com.gems.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantIdentifierValidatorTest {

    @Test
    void sanitize_NormalizesTrimAndLowercase() {
        assertEquals("acme", TenantIdentifierValidator.sanitize("  ACME  "));
        assertEquals("client_01", TenantIdentifierValidator.sanitize("Client_01"));
    }

    @Test
    void sanitize_WithNullOrBlank_Throws() {
        assertThrows(IllegalArgumentException.class, () -> TenantIdentifierValidator.sanitize(null));
        assertThrows(IllegalArgumentException.class, () -> TenantIdentifierValidator.sanitize("   "));
    }

    @Test
    void sanitize_WithSqlInjectionAttempt_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> TenantIdentifierValidator.sanitize("public; DROP TABLE users; --"));
        assertThrows(IllegalArgumentException.class,
                () -> TenantIdentifierValidator.sanitize("acme schema"));
        assertThrows(IllegalArgumentException.class,
                () -> TenantIdentifierValidator.sanitize("acme\"quote"));
    }
}
