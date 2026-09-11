package br.com.gems.exception.handler;

import br.com.gems.exception.exception.BusinessException;
import br.com.gems.exception.exception.ExternalServiceException;
import br.com.gems.exception.exception.handler.AuthorizationExceptionHandler;
import br.com.gems.exception.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-006 — cada família de falha sai com o status contratado.
 * <p>
 * O teste faz a requisição passar pela pilha do Spring em vez de chamar o método do handler: o
 * status vem de {@code @ResponseStatus}, e chamar o método direto devolveria o envelope sem nunca
 * exercitar a anotação. Um handler com o status errado passaria despercebido.
 * </p>
 */
class ErrorStatusCoverageTest {

    private MockMvc pilha;

    @BeforeEach
    void montarPilha() {
        pilha = MockMvcBuilders.standaloneSetup( new ControladorDeTeste() )
                .setControllerAdvice( new AuthorizationExceptionHandler(), new GlobalExceptionHandler() )
                .build();
    }

    @Test
    void validacaoDevolve400() throws Exception {
        pilha.perform( get( "/validacao" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.errorType" ).value( "VALIDACAO" ) )
                .andExpect( jsonPath( "$.detalhes.length()" ).value( 2 ) );
    }

    @Test
    void regraDeNegocioDevolve400() throws Exception {
        pilha.perform( get( "/negocio" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.codigo" ).value( "ORG-409" ) )
                .andExpect( jsonPath( "$.message" ).value( "Organização já cadastrada" ) );
    }

    @Test
    void autorizacaoNegadaDevolve403() throws Exception {
        pilha.perform( get( "/autorizacao" ) )
                .andExpect( status().isForbidden() )
                .andExpect( jsonPath( "$.errorType" ).value( "ACESSO_NEGADO" ) );
    }

    @Test
    void servicoExternoIndisponivelDevolve502() throws Exception {
        pilha.perform( get( "/externo" ) )
                .andExpect( status().isBadGateway() )
                .andExpect( jsonPath( "$.errorType" ).value( "SERVICO_INDISPONIVEL" ) );
    }

    /**
     * O nome do serviço que falhou fica no log, não na resposta: dizer ao cliente qual provedor
     * caiu descreve a topologia interna sem lhe dar nada acionável.
     */
    @Test
    void respostaDe502NaoNomeiaOServicoQueFalhou() throws Exception {
        pilha.perform( get( "/externo" ) )
                .andExpect( status().isBadGateway() )
                .andExpect( jsonPath( "$.message" ).value( "Serviço temporariamente indisponível" ) );
    }

    @RestController
    static class ControladorDeTeste {

        @GetMapping( "/validacao" )
        public void validacao() throws NoSuchMethodException, MethodArgumentNotValidException {
            var vinculo = new BeanPropertyBindingResult( new Object(), "corpo" );
            vinculo.addError( new FieldError( "corpo", "nome", "não pode ser vazio" ) );
            vinculo.addError( new FieldError( "corpo", "email", "formato inválido" ) );

            var parametro = new MethodParameter(
                    ControladorDeTeste.class.getDeclaredMethod( "validacao" ), -1 );

            throw new MethodArgumentNotValidException( parametro, vinculo );
        }

        @GetMapping( "/negocio" )
        public void negocio() {
            throw new BusinessException( br.com.gems.exception.exception.enums.ErrorTypeEnum.FALHA,
                    "Organização já cadastrada", "ORG-409" );
        }

        @GetMapping( "/autorizacao" )
        public void autorizacao() {
            throw new AccessDeniedException( "sem a ação exigida" );
        }

        @GetMapping( "/externo" )
        public void externo() {
            throw new ExternalServiceException( "keycloak", "Serviço temporariamente indisponível" );
        }

    }

}
