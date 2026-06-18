package br.com.gems.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateUtilTest {

    @Test
    void isValidBirthDate_WithNullLocalDate_ReturnsFalse() {
        assertFalse(DateUtil.isValidBirthDate((LocalDate) null));
    }

    @Test
    void isValidBirthDate_WithFutureLocalDate_ReturnsFalse() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        assertFalse(DateUtil.isValidBirthDate(futureDate));
    }

    @Test
    void isValidBirthDate_WithPastLocalDate_ReturnsTrue() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        assertTrue(DateUtil.isValidBirthDate(pastDate));
    }

    @Test
    void isValidBirthDate_WithNullLocalDateTime_ReturnsFalse() {
        assertFalse(DateUtil.isValidBirthDate((LocalDateTime) null));
    }

    @Test
    void isValidBirthDate_WithFutureLocalDateTime_ReturnsFalse() {
        LocalDateTime futureDate = LocalDateTime.now().plusDays(1);
        assertFalse(DateUtil.isValidBirthDate(futureDate));
    }

    @Test
    void isValidBirthDate_WithPastLocalDateTime_ReturnsTrue() {
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
        assertTrue(DateUtil.isValidBirthDate(pastDate));
    }
}
