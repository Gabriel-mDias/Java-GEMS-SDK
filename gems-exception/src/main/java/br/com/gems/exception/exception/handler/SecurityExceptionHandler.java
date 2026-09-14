package br.com.gems.exception.exception.handler;

import br.com.gems.exception.base.BaseController;
import br.com.gems.exception.exception.dto.ExceptionResponseDTO;
import br.com.gems.exception.exception.enums.ErrorTypeEnum;
import br.com.gems.utils.ObjectUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * Falha de <b>autenticação</b> — 401.
 * <p>
 * Até a 3.0.0 esta classe também respondia {@code AuthorizationDeniedException} com 401. Isso era
 * um defeito: a exceção sobe de {@code @PreAuthorize} para quem <b>já está autenticado</b> e não
 * tem a ação — é negação de autorização, 403. Como ela estende {@code AccessDeniedException},
 * o {@link AuthorizationExceptionHandler} já a cobre por herança; o handler daqui era mais
 * específico e vencia, mandando o frontend a um login que não resolvia nada. Na 3.1.0 o método
 * saiu, e o que resta aqui é só o que de fato é autenticação.
 * </p>
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnClass( BadCredentialsException.class )
public class SecurityExceptionHandler {

    @ExceptionHandler( BadCredentialsException.class )
    @ResponseStatus( HttpStatus.UNAUTHORIZED )
    public ExceptionResponseDTO handleCredentialsException( BadCredentialsException ex, HttpServletRequest request ) {
        var error = ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ErrorTypeEnum.FALHA )
                .message( "Falha ao autenticar" )
                .path( request.getRequestURI() )
                .method( request.getMethod() )
                .build();

        logFalhaOrAlerta( error, request );
        return error;
    }

    private void logFalhaOrAlerta( ExceptionResponseDTO error, HttpServletRequest request ) {
        log.error( error.toString(), this.getBodyRequest( request ) );
    }

    private Object getBodyRequest( HttpServletRequest request ) {
        var body = request.getAttribute( BaseController.REQUEST_BODY_ATTRIBUTE );
        return ObjectUtil.isNullOrEmpty( body ) ? "The request not informed a body" : body;
    }

}
