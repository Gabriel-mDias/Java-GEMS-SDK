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
     * Construtor para informar o tipo de erro detalhado juntamente com a mensagem.
     * @param errorType Enum que tipifica a natureza do erro.
     * @param message Mensagem amigável que será enviada ao cliente.
     */
    public BusinessException(ErrorTypeEnum errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    /**
     * Construtor para lançar uma exceção de negócio genérica, assumindo {@link ErrorTypeEnum#FALHA}.
     * @param message A mensagem da exceção.
     */
    public BusinessException( String message ){
        super( message );
        this.errorType = ErrorTypeEnum.FALHA;
    }

    /**
     * Construtor que recebe uma lista de erros, utilitário para consolidação de falhas em validações de formulário.
     * @param messages Lista de strings contendo todas as violações de regras.
     */
    public BusinessException( List<String> messages ){
        super( String.join("\n", messages));
        this.errorType = ErrorTypeEnum.FALHA;
    }

}
