package br.com.gems.exception.handler;

import br.com.gems.exception.exception.handler.AuthorizationExceptionHandler;
import br.com.gems.exception.exception.handler.GlobalExceptionHandler;
import br.com.gems.exception.exception.handler.SecurityExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Correção da 3.1.0 — {@code AuthorizationDeniedException} responde <b>403</b>, não 401.
 * <p>
 * A pilha inclui o {@link SecurityExceptionHandler} de propósito: é o cenário do consumidor que
 * escaneia {@code br.com.gems} e recebe os três advices da SDK sem handler próprio. Na 3.0.0 esse
 * handler declarava um método para a exceção, com 401 — a 3.1.0 o removeu, e as duas provas
 * abaixo fixam o status e a ausência do método.
 * </p>
 */
class AuthorizationDeniedRespondeForbiddenTest {

    private MockMvc pilha;

    @BeforeEach
    void montarPilha() {
        pilha = MockMvcBuilders.standaloneSetup( new ControladorDeTeste() )
                .setControllerAdvice( new SecurityExceptionHandler(), new AuthorizationExceptionHandler(),
                        new GlobalExceptionHandler() )
                .build();
    }

    @Test
    void negacaoPorPreAuthorizeDevolve403ComOEnvelopeDeAcessoNegado() throws Exception {
        pilha.perform( get( "/pre-authorize" ) )
                .andExpect( status().isForbidden() )
                .andExpect( jsonPath( "$.errorType" ).value( "ACESSO_NEGADO" ) )
                .andExpect( jsonPath( "$.path" ).value( "/pre-authorize" ) );
    }

    /**
     * A prova de status acima <b>não</b> cai quando o método volta: o {@code @Order} do
     * {@link AuthorizationExceptionHandler} o põe antes na fila do resolver, e o Spring fica com o
     * primeiro advice que tem handler compatível — o 403 vencia por ordem já na 3.0.0, e a
     * mutação foi verificada inerte contra ela. O que a 3.1.0 garante de fato é que <b>nenhum
     * advice da SDK mapeia {@code AuthorizationDeniedException} para 401</b>, e isso se prova na
     * estrutura: um handler declarado para a exceção, com qualquer ordem, reprova aqui.
     */
    @Test
    void nenhumAdviceDaSdkDeclaraHandlerParaAuthorizationDeniedException() {
        var advices = List.of( SecurityExceptionHandler.class, AuthorizationExceptionHandler.class,
                GlobalExceptionHandler.class );

        var handlersDaExcecao = advices.stream()
                .flatMap( advice -> Arrays.stream( advice.getDeclaredMethods() ) )
                .filter( metodo -> metodo.isAnnotationPresent( ExceptionHandler.class ) )
                .filter( metodo -> Arrays.asList( metodo.getAnnotation( ExceptionHandler.class ).value() )
                        .contains( AuthorizationDeniedException.class ) )
                .map( Method::getName )
                .toList();

        assertThat( handlersDaExcecao ).isEmpty();
    }

    @RestController
    static class ControladorDeTeste {

        @GetMapping( "/pre-authorize" )
        public void preAuthorize() {
            throw new AuthorizationDeniedException( "Access Denied" );
        }

    }

}
