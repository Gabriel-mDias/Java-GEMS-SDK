package br.com.gems.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.gems.exception.exception.BusinessException;

class BusinessExceptionTest {

    @Test
    void constructor_WithString_SetsMessageAndDefaultErrorType() {
        String errorMessage = "Business rule violated";
        BusinessException exception = new BusinessException(errorMessage);

        assertEquals(errorMessage, exception.getMessage());
        assertEquals("FALHA", exception.getErrorType().name());
    }

    @Test
    void constructor_WithList_SetsConcatenatedMessageAndDefaultErrorType() {
        List<String> errorMessages = Arrays.asList("Error 1", "Error 2");
        BusinessException exception = new BusinessException(errorMessages);

        assertTrue(exception.getMessage().contains("Error 1"));
        assertTrue(exception.getMessage().contains("Error 2"));
        assertTrue(exception.getMessage().contains("\n"));
        assertEquals("FALHA", exception.getErrorType().name());
    }

    @Test
    void constructor_WithEmptyList_SetsEmptyMessage() {
        List<String> emptyList = Arrays.asList();
        BusinessException exception = new BusinessException(emptyList);

        assertTrue(exception.getMessage().isEmpty());
    }
}
