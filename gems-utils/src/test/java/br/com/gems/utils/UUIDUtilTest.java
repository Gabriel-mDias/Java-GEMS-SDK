package br.com.gems.utils;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UUIDUtilTest {

    @Test
    void fromStringOrNull_WithValidUuid_ReturnsUuid() {
        UUID expected = UUID.randomUUID();
        assertEquals(expected, UUIDUtil.fromStringOrNull(expected.toString()));
    }

    @Test
    void fromStringOrNull_WithNull_ReturnsNull() {
        assertNull(UUIDUtil.fromStringOrNull(null));
    }

    @Test
    void fromStringOrNull_WithEmpty_ReturnsNull() {
        assertNull(UUIDUtil.fromStringOrNull(""));
    }

    @Test
    void fromStringOrNull_WithInvalidUuid_Throws() {
        assertThrows(IllegalArgumentException.class, () -> UUIDUtil.fromStringOrNull("not-a-uuid"));
    }
}
