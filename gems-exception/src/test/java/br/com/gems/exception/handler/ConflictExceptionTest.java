package br.com.gems.exception.handler;

import br.com.gems.exception.exception.BusinessException;
import br.com.gems.exception.exception.ConflictException;
import br.com.gems.exception.exception.enums.ErrorTypeEnum;
import br.com.gems.exception.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 3.1.0 — {@link ConflictException} é 409, e o envelope é o mesmo de {@link BusinessException}:
 * {@code codigo} e {@code detalhes} acompanham a mensagem (EX-1). Trocar {@code extends
 * ConflictException} por {@code extends BusinessException} numa exceção de consumidor faz o 409
 * virar 400 — é a segunda mutação do contrato, provada no Meduc.
 */
class ConflictExceptionTest {

    private MockMvc pilha;

    @BeforeEach
    void montarPilha() {
        pilha = MockMvcBuilders.standaloneSetup( new ControladorDeTeste() )
                .setControllerAdvice( new GlobalExceptionHandler() )
                .build();
    }

    @Test
    void conflitoDevolve409ComCodigoEDetalhesAoLadoDaMensagem() throws Exception {
        pilha.perform( get( "/conflito" ) )
                .andExpect( status().isConflict() )
                .andExpect( jsonPath( "$.errorType" ).value( "FALHA" ) )
                .andExpect( jsonPath( "$.message" ).value( "Não é possível desabilitar o último gestor" ) )
                .andExpect( jsonPath( "$.codigo" ).value( "ULTIMO_GESTOR_HABILITADO" ) )
                .andExpect( jsonPath( "$.detalhes[0]" ).value( "contexto: preservado" ) )
                .andExpect( jsonPath( "$.path" ).value( "/conflito" ) );
    }

    @Test
    void conflitoComTipoProprioPreservaOTipo() throws Exception {
        pilha.perform( get( "/conflito-alerta" ) )
                .andExpect( status().isConflict() )
                .andExpect( jsonPath( "$.errorType" ).value( "ALERTA" ) );
    }

    @Test
    void regraDeNegocioQueNaoEConflitoContinuaEm400() throws Exception {
        pilha.perform( get( "/negocio" ) )
                .andExpect( status().isBadRequest() );
    }

    @RestController
    static class ControladorDeTeste {

        @GetMapping( "/conflito" )
        public void conflito() {
            throw new ConflictException( "Não é possível desabilitar o último gestor",
                    "ULTIMO_GESTOR_HABILITADO", List.of( "contexto: preservado" ) );
        }

        @GetMapping( "/conflito-alerta" )
        public void conflitoAlerta() {
            throw new ConflictException( ErrorTypeEnum.ALERTA, "Já existe", "DUPLICADO", null );
        }

        @GetMapping( "/negocio" )
        public void negocio() {
            throw new BusinessException( "Situação inválida" );
        }

    }

}
