package br.com.gems.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailUtilTest {

    @Test
    void isValid_WithSimpleEmail_ReturnsTrue() {
        assertTrue(EmailUtil.isValid("user@example.com"));
    }

    @Test
    void isValid_WithDotAndHyphen_ReturnsTrue() {
        assertTrue(EmailUtil.isValid("first.last@my-domain.com"));
    }

    @Test
    void isValid_WithLongTld_ReturnsTrue() {
        assertTrue(EmailUtil.isValid("contact@empresa.technology"));
    }

    @Test
    void isValid_WithoutAt_ReturnsFalse() {
        assertFalse(EmailUtil.isValid("userexample.com"));
    }

    @Test
    void isValid_WithDoubleAt_ReturnsFalse() {
        assertFalse(EmailUtil.isValid("user@@example.com"));
    }

    @Test
    void isValid_WithSpace_ReturnsFalse() {
        assertFalse(EmailUtil.isValid("user name@example.com"));
    }

    @Test
    void isValid_WithNullOrEmpty_ReturnsFalse() {
        assertFalse(EmailUtil.isValid(null));
        assertFalse(EmailUtil.isValid(""));
    }
}
