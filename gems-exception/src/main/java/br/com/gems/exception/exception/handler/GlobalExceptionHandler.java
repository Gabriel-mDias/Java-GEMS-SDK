package br.com.gems.exception.exception.handler;

import br.com.gems.exception.base.BaseController;
import br.com.gems.exception.exception.BusinessException;
import br.com.gems.exception.exception.ConflictException;
import br.com.gems.exception.exception.ExternalServiceException;
import br.com.gems.exception.exception.SecurityException;
import br.com.gems.exception.exception.dto.ExceptionResponseDTO;
import br.com.gems.exception.exception.enums.ErrorTypeEnum;
import br.com.gems.utils.ObjectUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Implementação "default" da manipulação de exceções. Flexível para
 * novas implementações customizadas.
 * <p>
 * <b>Política de mensagem nos erros de requisição (404 e 400 de parâmetro):</b> a resposta não
 * ecoa o caminho pedido nem o valor recebido. Repetir o caminho num 404 devolve ao cliente o que ele
 * mesmo digitou e vira vetor de reflexão; repetir o valor num 400 faz o mesmo com o conteúdo do
 * parâmetro. O que é útil ao cliente — o <b>nome</b> do parâmetro errado — vai; o resto fica no log.
 * </p>
 * <p>
 * {@code path} é {@code request.getRequestURI()} em todos os handlers: {@code getServletPath()}
 * devolve vazio no MockMvc e coincide com a URI só quando o dispatcher está em {@code /}.
 * </p>
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
                .path( request.getRequestURI() )
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
                .path( request.getRequestURI() )
                .method( request.getMethod() )
                .build();

        logFalhaOrAlerta( error, request );
        return error;
    }

    /**
     * Conflito com o estado atual — 409. Declarado antes de {@link BusinessException} por
     * legibilidade; o Spring escolheria o tipo mais específico de qualquer forma.
     */
    @ExceptionHandler( ConflictException.class )
    @ResponseStatus( HttpStatus.CONFLICT )
    public ExceptionResponseDTO handleConflictException( ConflictException ex, HttpServletRequest request ) {
        var error = envelopeDeNegocio( ex, request );

        logFalhaOrAlerta( error, request );
        return error;
    }

    @ExceptionHandler( BusinessException.class )
    @ResponseStatus( HttpStatus.BAD_REQUEST )
    public ExceptionResponseDTO handleException( BusinessException ex, HttpServletRequest request ) {
        var error = envelopeDeNegocio( ex, request );

        logFalhaOrAlerta( error, request );
        return error;
    }

    /**
     * Validação acumulada do corpo da requisição. Todas as violações vão em {@code detalhes};
     * a mensagem é o resumo. Devolver só a primeira violação obrigaria o cliente a corrigir
     * um campo por vez.
     */
    @ExceptionHandler( MethodArgumentNotValidException.class )
    @ResponseStatus( HttpStatus.BAD_REQUEST )
    public ExceptionResponseDTO handleValidationException( MethodArgumentNotValidException ex,
                                                           HttpServletRequest request ) {
        var violacoes = ex.getBindingResult().getFieldErrors().stream()
                .map( campo -> campo.getField() + ": " + campo.getDefaultMessage() )
                .toList();

        var error = ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ErrorTypeEnum.VALIDACAO )
                .message( String.join( "\n", violacoes ) )
                .detalhes( violacoes )
                .path( request.getRequestURI() )
                .method( request.getMethod() )
                .build();

        logFalhaOrAlerta( error, request );
        return error;
    }

    /**
     * Parâmetro de caminho ou de consulta num formato que não converte para o tipo declarado —
     * 400. Nomeia o parâmetro e nunca o valor recebido (política no Javadoc da classe).
     */
    @ExceptionHandler( MethodArgumentTypeMismatchException.class )
    @ResponseStatus( HttpStatus.BAD_REQUEST )
    public ExceptionResponseDTO handleTypeMismatchException( MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest request ) {
        var error = ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ErrorTypeEnum.VALIDACAO )
                .codigo( "PARAMETRO_INVALIDO" )
                .message( "O parâmetro '" + ex.getName() + "' foi informado em um formato inválido." )
                .path( request.getRequestURI() )
                .method( request.getMethod() )
                .build();

        logFalhaOrAlerta( error, request );
        return error;
    }

    /**
     * Endereço não mapeado — 404, e não o 500 do catch-all. Mensagem fixa: o caminho pedido fica
     * no {@code path} do envelope e no log, não na mensagem (política no Javadoc da classe).
     */
    @ExceptionHandler( NoResourceFoundException.class )
    @ResponseStatus( HttpStatus.NOT_FOUND )
    public ExceptionResponseDTO handleNoResourceFoundException( NoResourceFoundException ex,
                                                                HttpServletRequest request ) {
        var error = ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ErrorTypeEnum.FALHA )
                .codigo( "RECURSO_NAO_ENCONTRADO" )
                .message( "Recurso não encontrado." )
                .path( request.getRequestURI() )
                .method( request.getMethod() )
                .build();

        logFalhaOrAlerta( error, request );
        return error;
    }

    /**
     * Serviço fora do processo indisponível. A mensagem do cliente não nomeia o serviço —
     * quem falhou é problema de operação, e vai para o log.
     */
    @ExceptionHandler( ExternalServiceException.class )
    @ResponseStatus( HttpStatus.BAD_GATEWAY )
    public ExceptionResponseDTO handleExternalServiceException( ExternalServiceException ex,
                                                                HttpServletRequest request ) {
        var error = ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ErrorTypeEnum.SERVICO_INDISPONIVEL )
                .message( ex.getMessage() )
                .path( request.getRequestURI() )
                .method( request.getMethod() )
                .build();

        log.error( "Servico externo indisponivel: {} - {}", ex.getServico(), error, ex );
        return error;
    }

    @ExceptionHandler( IllegalArgumentException.class )
    @ResponseStatus( HttpStatus.BAD_REQUEST )
    public ExceptionResponseDTO handleArgumentException( IllegalArgumentException ex, HttpServletRequest request ) {
        var error = ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ErrorTypeEnum.FALHA )
                .message( ex.getMessage() )
                .path( request.getRequestURI() )
                .method( request.getMethod() )
                .build();

        logFalhaOrAlerta( error, request );
        return error;
    }

    /** Envelope de regra de negócio: código e detalhes acompanham a mensagem (EX-1). */
    private ExceptionResponseDTO envelopeDeNegocio( BusinessException ex, HttpServletRequest request ) {
        return ExceptionResponseDTO.builder()
                .occurrenceTime( LocalDateTime.now() )
                .errorType( ex.getErrorType() )
                .message( ex.getMessage() )
                .codigo( ex.getCodigo() )
                .detalhes( ex.getDetalhes() )
                .path( request.getRequestURI() )
                .method( request.getMethod() )
                .build();
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
