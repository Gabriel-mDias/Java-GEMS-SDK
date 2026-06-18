package br.com.gems.exception.exception.handler;

import br.com.gems.exception.base.BaseController;
import br.com.gems.exception.exception.BusinessException;
import br.com.gems.exception.exception.SecurityException;
import br.com.gems.exception.exception.dto.ExceptionResponseDTO;
import br.com.gems.exception.exception.enums.ErrorTypeEnum;
import br.com.gems.utils.ObjectUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Implementação "default" da manipulação de exceções. Flexível para
 * novas implementações customizadas.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler( Exception.class )
    @ResponseStatus( HttpStatus.INTERNAL_SERVER_ERROR )
    public ExceptionResponseDTO handleException( Exception ex, HttpServletRequest request ) {
        var error = ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ErrorTypeEnum.ERRO_NAO_ESPERADO )
                .path( request.getServletPath() )
                .method( request.getMethod() )
                .build();

        logError( error, request, ex );
        return error;
    }

    @ExceptionHandler( SecurityException.class )
    @ResponseStatus( HttpStatus.UNAUTHORIZED )
    public ExceptionResponseDTO handleSecurityException( SecurityException ex, HttpServletRequest request ) {
        var error = ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ErrorTypeEnum.FALHA )
                .message( ex.getMessage() )
                .path( request.getServletPath() )
                .method( request.getMethod() )
                .build();

        logFalhaOrAlerta( error, request );
        return error;
    }

    @ExceptionHandler( BusinessException.class )
    @ResponseStatus( HttpStatus.BAD_REQUEST )
    public ExceptionResponseDTO handleException( BusinessException ex, HttpServletRequest request ) {
        var error = ExceptionResponseDTO.builder()
                        .occurrenceTime( LocalDateTime.now() )
                        .errorType( ex.getErrorType() )
                        .message( ex.getMessage() )
                        .path( request.getServletPath() )
                        .method( request.getMethod() )
                        .build();

        logFalhaOrAlerta( error, request );
        return error;
    }

    @ExceptionHandler( IllegalArgumentException.class )
    @ResponseStatus( HttpStatus.BAD_REQUEST )
    public ExceptionResponseDTO handleArgumentException( IllegalArgumentException ex, HttpServletRequest request ) {
        var error = ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ErrorTypeEnum.FALHA )
                .message( ex.getMessage() )
                .path( request.getServletPath() )
                .method( request.getMethod() )
                .build();

        logFalhaOrAlerta( error, request );
        return error;
    }

    private void logError( ExceptionResponseDTO error, HttpServletRequest request, Exception exception ) {
        var code = UUID.randomUUID();
        var logMessage = "ERROR: " + code.toString();
        var responseMessage = "Entre em contato com o administrador com o seguinte código: " + code.toString();

        log.error( logMessage, exception );
        error.setMessage( responseMessage );
    }

    private void logFalhaOrAlerta( ExceptionResponseDTO error, HttpServletRequest request ) {
        log.error( error.toString(), this.getBodyRequest( request ) );
    }

    private Object getBodyRequest( HttpServletRequest request ) {
        var body = request.getAttribute( BaseController.REQUEST_BODY_ATTRIBUTE );
        // BUGFIX: Return body instead of string when body exists.
        return ObjectUtil.isNullOrEmpty( body ) ? "The request not informed a body" : body;
    }

}
