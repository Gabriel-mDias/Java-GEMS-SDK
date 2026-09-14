package br.com.gems.exception.handler;

import br.com.gems.exception.exception.BusinessException;
import br.com.gems.exception.exception.enums.ErrorTypeEnum;
import br.com.gems.exception.exception.handler.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * EX-1 — código e detalhes <b>acompanham</b> a mensagem; nunca a substituem.
 * <p>
 * A afirmação parece óbvia até alguém escrever {@code .message(ex.getCodigo())} para "não perder o
 * código", e o cliente passar a ler {@code MAT-409} no lugar do texto que explicava o que houve.
 * O que estes testes fixam é que os três campos coexistem.
 * </p>
 */
class ErrorEnvelopeCompositionTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void prepararHandler() {
        MockitoAnnotations.openMocks( this );
        handler = new GlobalExceptionHandler();
        when( request.getRequestURI() ).thenReturn( "/api/matriculas" );
        when( request.getMethod() ).thenReturn( "POST" );
    }

    @Test
    void codigoAcompanhaAMensagemEmVezDeSubstituila() {
        var excecao = new BusinessException( ErrorTypeEnum.FALHA, "Matrícula já existe para o aluno", "MAT-409" );

        var resposta = handler.handleException( excecao, request );

        assertThat( resposta.getMessage() ).isEqualTo( "Matrícula já existe para o aluno" );
        assertThat( resposta.getCodigo() ).isEqualTo( "MAT-409" );
    }

    @Test
    void detalhesAcompanhamAMensagemEmVezDeSubstituila() {
        var violacoes = List.of( "nome é obrigatório", "data de nascimento é obrigatória" );
        var excecao = new BusinessException( violacoes );

        var resposta = handler.handleException( excecao, request );

        assertThat( resposta.getMessage() ).contains( "nome é obrigatório" )
                .contains( "data de nascimento é obrigatória" );
        assertThat( resposta.getDetalhes() ).containsExactlyElementsOf( violacoes );
    }

    @Test
    void envelopeSemCodigoNemDetalhesNaoInventaValor() {
        var resposta = handler.handleException( new BusinessException( "Situação inválida" ), request );

        assertThat( resposta.getMessage() ).isEqualTo( "Situação inválida" );
        assertThat( resposta.getCodigo() ).isNull();
        assertThat( resposta.getDetalhes() ).isNull();
    }

    @Test
    void validacaoAcumuladaListaTodasAsViolacoesSemPerderNenhuma() throws NoSuchMethodException {
        var resposta = handler.handleValidationException( duasViolacoes(), request );

        assertThat( resposta.getErrorType() ).isEqualTo( ErrorTypeEnum.VALIDACAO );
        assertThat( resposta.getDetalhes() ).containsExactly( "nome: não pode ser vazio",
                "email: formato inválido" );
        assertThat( resposta.getMessage() ).contains( "nome" ).contains( "email" );
    }

    /**
     * Constrói a exceção de validação à mão em vez de anotar um corpo com {@code @Valid}: o módulo
     * não declara implementação de Bean Validation, e o teste passaria a provar que o validador
     * existe em vez de provar o que o handler faz com o resultado dele.
     */
    private MethodArgumentNotValidException duasViolacoes() throws NoSuchMethodException {
        var vinculo = new BeanPropertyBindingResult( new Object(), "corpo" );
        vinculo.addError( new FieldError( "corpo", "nome", "não pode ser vazio" ) );
        vinculo.addError( new FieldError( "corpo", "email", "formato inválido" ) );

        var parametro = new MethodParameter(
                ErrorEnvelopeCompositionTest.class.getDeclaredMethod( "duasViolacoes" ), -1 );

        return new MethodArgumentNotValidException( parametro, vinculo );
    }

}
