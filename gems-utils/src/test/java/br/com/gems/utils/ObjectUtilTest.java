package br.com.gems.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectUtilTest {

    @Test
    void isNullOrEmpty_WithNull_ReturnsTrue() {
        assertTrue(ObjectUtil.isNullOrEmpty(null));
    }

    @Test
    void isNullOrEmpty_WithEmptyString_ReturnsTrue() {
        assertTrue(ObjectUtil.isNullOrEmpty(""));
        assertTrue(ObjectUtil.isNullOrEmpty("   "));
    }

    @Test
    void isNullOrEmpty_WithEmptyCollection_ReturnsTrue() {
        assertTrue(ObjectUtil.isNullOrEmpty(new ArrayList<>()));
    }

    @Test
    void isNullOrEmpty_WithValidString_ReturnsFalse() {
        assertFalse(ObjectUtil.isNullOrEmpty("Valid String"));
    }

    @Test
    void isNullOrEmpty_WithValidCollection_ReturnsFalse() {
        List<String> list = new ArrayList<>();
        list.add("Item");
        assertFalse(ObjectUtil.isNullOrEmpty(list));
    }

    @Test
    void isNullOrEmpty_WithObject_ReturnsFalse() {
        assertFalse(ObjectUtil.isNullOrEmpty(new Object()));
        assertFalse(ObjectUtil.isNullOrEmpty(10));
    }

    @Test
    void isNotNullAndNotEmpty_WithValidString_ReturnsTrue() {
        assertTrue(ObjectUtil.isNotNullAndNotEmpty("Valid String"));
    }

    @Test
    void isNotNullAndNotEmpty_WithNull_ReturnsFalse() {
        assertFalse(ObjectUtil.isNotNullAndNotEmpty(null));
    }
}
