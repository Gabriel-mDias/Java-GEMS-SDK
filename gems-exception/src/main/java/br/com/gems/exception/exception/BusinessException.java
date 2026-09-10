package br.com.gems.exception.exception;

import br.com.gems.exception.exception.enums.ErrorTypeEnum;
import lombok.Getter;

import java.util.List;

/**
 * Exceção padrão para violações de Regras de Negócio em todo o ecossistema GEMS.
 * <p>
 * Lançar essa exceção garante que os handlers globais da aplicação interceptem o erro
 * e o convertam em uma resposta HTTP 400 (Bad Request) ou similar, padronizando a
 * comunicação de erros com o frontend.
 * </p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorTypeEnum errorType;

    /**
     * Código de domínio, quando o emissor tem um. O handler o repassa ao envelope
     * <b>ao lado</b> da mensagem — ver EX-1 no contrato do módulo.
     */
    private final String codigo;

    /** Violações acumuladas, quando a falha tem mais de uma causa. */
    private final List<String> detalhes;

    /**
     * Construtor para informar o tipo de erro detalhado juntamente com a mensagem.
     * @param errorType Enum que tipifica a natureza do erro.
     * @param message Mensagem amigável que será enviada ao cliente.
     */
    public BusinessException(ErrorTypeEnum errorType, String message) {
        this( errorType, message, null, null );
    }

    /**
     * Construtor para lançar uma exceção de negócio genérica, assumindo {@link ErrorTypeEnum#FALHA}.
     * @param message A mensagem da exceção.
     */
    public BusinessException( String message ){
        this( ErrorTypeEnum.FALHA, message, null, null );
    }

    /**
     * Construtor que recebe uma lista de erros, utilitário para consolidação de falhas em validações de formulário.
     * @param messages Lista de strings contendo todas as violações de regras.
     */
    public BusinessException( List<String> messages ){
        this( ErrorTypeEnum.FALHA, String.join( "\n", messages ), null, messages );
    }

    /**
     * Construtor que informa o código de domínio junto da mensagem.
     * @param errorType Enum que tipifica a natureza do erro.
     * @param message Mensagem amigável que será enviada ao cliente.
     * @param codigo Código de domínio do erro.
     */
    public BusinessException( ErrorTypeEnum errorType, String message, String codigo ) {
        this( errorType, message, codigo, null );
    }

    /**
     * Construtor completo. A mensagem continua sendo o texto que o cliente lê; código e
     * detalhes são informação adicional, e nenhum dos dois a substitui.
     * @param errorType Enum que tipifica a natureza do erro.
     * @param message Mensagem amigável que será enviada ao cliente.
     * @param codigo Código de domínio do erro, ou {@code null}.
     * @param detalhes Violações acumuladas, ou {@code null}.
     */
    public BusinessException( ErrorTypeEnum errorType, String message, String codigo, List<String> detalhes ) {
        super( message );
        this.errorType = errorType;
        this.codigo = codigo;
        this.detalhes = detalhes;
    }

}
