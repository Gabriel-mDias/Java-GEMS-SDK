package br.com.gems.exception.exception.dto;

import br.com.gems.exception.exception.enums.ErrorTypeEnum;
import br.com.gems.utils.ObjectUtil;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ExceptionResponseDTO {

    private LocalDateTime occurrenceTime;
    private ErrorTypeEnum errorType;
    private String message;
    private String path;
    private String method;

    /**
     * Código de domínio do erro, quando o emissor informa um. Acompanha a mensagem;
     * nunca a substitui — ver EX-1 no contrato do módulo.
     */
    private String codigo;

    /**
     * Violações acumuladas, quando a falha tem mais de uma causa. Acompanha a mensagem
     * pelo mesmo motivo que {@link #codigo}.
     */
    private List<String> detalhes;

    /**
     * Construtor da forma anterior a 3.0.0, preservado porque {@code @AllArgsConstructor}
     * passou a gerar uma assinatura maior. Consumidor que já o chamava continua compilando.
     */
    public ExceptionResponseDTO( LocalDateTime occurrenceTime, ErrorTypeEnum errorType, String message,
                                 String path, String method ) {
        this( occurrenceTime, errorType, message, path, method, null, null );
    }

    @Override
    public String toString() {
        var texto = new StringBuilder()
                .append( "An error occurred at: " )
                .append( this.occurrenceTime)
                .append( " with status: " )
                .append( this.errorType.name() )
                .append( " and message: " )
                .append( this.message )
                .append( " in path: " )
                .append( this.path )
                .append( " with method: " )
                .append( this.method );

        if ( ObjectUtil.isNotNullAndNotEmpty( this.codigo ) ) {
            texto.append( " with code: " ).append( this.codigo );
        }

        if ( ObjectUtil.isNotNullAndNotEmpty( this.detalhes ) ) {
            texto.append( " with details: " ).append( this.detalhes );
        }

        return texto.toString();
    }

}
