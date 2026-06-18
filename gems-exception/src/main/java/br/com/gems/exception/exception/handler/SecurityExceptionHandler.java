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
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
@ConditionalOnClass({BadCredentialsException.class, AuthorizationDeniedException.class})
public class SecurityExceptionHandler {

    @ExceptionHandler( BadCredentialsException.class )
    @ResponseStatus( HttpStatus.UNAUTHORIZED )
    public ExceptionResponseDTO handleCredentialsException( BadCredentialsException ex, HttpServletRequest request ) {
        var error = ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ErrorTypeEnum.FALHA )
                .message( "Falha ao autenticar" )
                .path( request.getServletPath() )
                .method( request.getMethod() )
                .build();

        logFalhaOrAlerta( error, request );
        return error;
    }

    @ExceptionHandler( AuthorizationDeniedException.class )
    @ResponseStatus( HttpStatus.UNAUTHORIZED )
    public ExceptionResponseDTO handleClaimsException( AuthorizationDeniedException ex, HttpServletRequest request ) {
        var error = ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ErrorTypeEnum.FALHA )
                .message( "Você não possui acesso para este serviço!" )
                .path( request.getServletPath() )
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
