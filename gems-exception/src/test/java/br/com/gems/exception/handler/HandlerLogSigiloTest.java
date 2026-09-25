package br.com.gems.exception.handler;

import br.com.gems.exception.base.BaseController;
import br.com.gems.exception.exception.BusinessException;
import br.com.gems.exception.exception.ExternalServiceException;
import br.com.gems.exception.exception.handler.AuthorizationExceptionHandler;
import br.com.gems.exception.exception.handler.GlobalExceptionHandler;
import br.com.gems.exception.exception.handler.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

/** Dados controlados pelo cliente não devem entrar no log dos handlers. */
@ExtendWith(OutputCaptureExtension.class)
class HandlerLogSigiloTest {

    private static final String SENTINELA = "segredo-sentinela-987654321";

    @Test
    void naoDeveRegistrarCausaCaminhoOuCorpo_quandoErrosSaoTratados(CapturedOutput output) {
        var request = new MockHttpServletRequest( "POST", "/api/cep/" + SENTINELA );
        request.setAttribute( BaseController.REQUEST_BODY_ATTRIBUTE, SENTINELA );
        var global = new GlobalExceptionHandler();

        global.handleException( new IllegalStateException( SENTINELA ), request );
        global.handleException( new BusinessException( SENTINELA ), request );
        global.handleExternalServiceException(
                new ExternalServiceException( "provedor", "Falha externa", new IllegalStateException( SENTINELA ) ),
                request );
        new AuthorizationExceptionHandler().handleAccessDeniedException(
                new AccessDeniedException( SENTINELA ), request );
        new SecurityExceptionHandler().handleCredentialsException(
                new BadCredentialsException( SENTINELA ), request );

        assertThat( output.getOut() + output.getErr() ).doesNotContain( SENTINELA );
    }
}
