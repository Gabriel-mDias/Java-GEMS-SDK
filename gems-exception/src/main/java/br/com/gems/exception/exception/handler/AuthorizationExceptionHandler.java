package br.com.gems.exception.exception.handler;

import br.com.gems.exception.exception.dto.ExceptionResponseDTO;
import br.com.gems.exception.exception.enums.ErrorTypeEnum;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * Autorização negada — <b>403</b>, não 401.
 * <p>
 * A distinção é a razão de este handler existir: 401 diz "não sei quem você é" e faz o cliente
 * tentar autenticar de novo; 403 diz "sei quem você é e isso não é seu". Devolver 401 a quem já
 * está autenticado manda o frontend a um fluxo de login que não resolve nada.
 * </p>
 * <p>
 * Trata {@link AccessDeniedException}, a raiz da família — inclusive
 * {@code AuthorizationDeniedException}, o que sobe de {@code @PreAuthorize}. Desde a 3.1.0 nenhum
 * handler da SDK é mais específico que este para essa família: o 401 que o
 * {@code SecurityExceptionHandler} dava a {@code AuthorizationDeniedException} saiu.
 * </p>
 */
@Slf4j
@RestControllerAdvice
@Order( Ordered.LOWEST_PRECEDENCE - 100 )
public class AuthorizationExceptionHandler {

    @ExceptionHandler( AccessDeniedException.class )
    @ResponseStatus( HttpStatus.FORBIDDEN )
    public ExceptionResponseDTO handleAccessDeniedException( AccessDeniedException ex,
                                                             HttpServletRequest request ) {
        var error = ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ErrorTypeEnum.ACESSO_NEGADO )
                .message( "Você não possui acesso para este serviço!" )
                .path( request.getRequestURI() )
                .method( request.getMethod() )
                .build();

        log.warn( "Acesso HTTP negado." );
        return error;
    }

}
