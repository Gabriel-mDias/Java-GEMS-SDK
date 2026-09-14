package br.com.gems.exception.handler;

import br.com.gems.exception.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 3.1.0 — os erros de requisição que a 3.0.0 deixava cair no 500 do catch-all, e o que cada um
 * <b>não</b> ecoa.
 * <p>
 * O 404 não repete o caminho na mensagem e o 400 não repete o valor do parâmetro: os dois são
 * texto que o cliente enviou, e devolvê-lo é reflexão. O que ajuda o cliente é o nome do parâmetro
 * — esse vai.
 * </p>
 */
class RequestErrorsTest {

    private MockMvc pilha;

    @BeforeEach
    void montarPilha() {
        pilha = MockMvcBuilders.standaloneSetup( new ControladorDeTeste() )
                .setControllerAdvice( new GlobalExceptionHandler() )
                .build();
    }

    @Test
    void enderecoNaoMapeadoDevolve404SemRepetirOCaminhoNaMensagem() throws Exception {
        pilha.perform( get( "/segredo/interno" ) )
                .andExpect( status().isNotFound() )
                .andExpect( jsonPath( "$.errorType" ).value( "FALHA" ) )
                .andExpect( jsonPath( "$.codigo" ).value( "RECURSO_NAO_ENCONTRADO" ) )
                .andExpect( jsonPath( "$.message" ).value( "Recurso não encontrado." ) )
                .andExpect( jsonPath( "$.message" ).value( not( containsString( "segredo" ) ) ) )
                .andExpect( jsonPath( "$.path" ).value( "/segredo/interno" ) );
    }

    @Test
    void parametroEmFormatoInvalidoDevolve400NomeandoOParametroENuncaOValor() throws Exception {
        pilha.perform( get( "/organizacoes/nao-e-um-numero" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( jsonPath( "$.errorType" ).value( "VALIDACAO" ) )
                .andExpect( jsonPath( "$.codigo" ).value( "PARAMETRO_INVALIDO" ) )
                .andExpect( jsonPath( "$.message" ).value( containsString( "'id'" ) ) )
                .andExpect( jsonPath( "$.message" ).value( not( containsString( "nao-e-um-numero" ) ) ) );
    }

    @RestController
    static class ControladorDeTeste {

        /**
         * Lançada à mão: no MockMvc standalone não há o handler de recursos estáticos que, na
         * aplicação real, é quem lança esta exceção para um endereço não mapeado.
         */
        @GetMapping( "/segredo/interno" )
        public void naoExiste() throws NoResourceFoundException {
            throw new NoResourceFoundException( HttpMethod.GET, "/segredo/interno", "/segredo/interno" );
        }

        @GetMapping( "/organizacoes/{id}" )
        public void porId( @PathVariable Long id ) {
        }

    }

}
