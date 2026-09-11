package br.com.gems.exception.exception;

import lombok.Getter;

/**
 * Falha de um serviço do qual a aplicação depende — provedor de identidade, gateway,
 * qualquer coisa fora do processo.
 * <p>
 * Existe para separar "o pedido está errado" de "o pedido está certo e quem responde
 * não respondeu": a primeira é 4xx e o cliente corrige; a segunda é <b>502</b> e o
 * cliente só pode tentar de novo. Sem essa distinção, indisponibilidade de terceiro
 * chega ao cliente como erro dele.
 * </p>
 */
@Getter
public class ExternalServiceException extends RuntimeException {

    /** Nome do serviço que falhou, para o log. Não é enviado ao cliente. */
    private final String servico;

    public ExternalServiceException( String servico, String message ) {
        super( message );
        this.servico = servico;
    }

    public ExternalServiceException( String servico, String message, Throwable causa ) {
        super( message, causa );
        this.servico = servico;
    }

}
