package br.com.gems.exception.handler;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import br.com.gems.exception.exception.BusinessException;
import br.com.gems.exception.exception.dto.ExceptionResponseDTO;
import br.com.gems.exception.exception.handler.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new GlobalExceptionHandler();
        when(request.getServletPath()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
    }

    @Test
    void handleException_ReturnsInternalServerError() {
        Exception exception = new Exception("Unexpected error");

        ExceptionResponseDTO response = handler.handleException(exception, request);

        assertNotNull(response);
        assertEquals("ERRO_NAO_ESPERADO", response.getErrorType().name());
        assertEquals("/api/test", response.getPath());
        assertEquals("GET", response.getMethod());
        assertTrue(response.getMessage().contains("Entre em contato com o administrador"));
    }

    @Test
    void handleBusinessException_ReturnsBadRequest() {
        BusinessException exception = new BusinessException(Arrays.asList("Error 1", "Error 2"));

        ExceptionResponseDTO response = handler.handleException(exception, request);

        assertNotNull(response);
        assertEquals("FALHA", response.getErrorType().name());
        assertEquals("/api/test", response.getPath());
        assertEquals("GET", response.getMethod());
        assertTrue(response.getMessage().contains("Error 1"));
        assertTrue(response.getMessage().contains("Error 2"));
    }
}
