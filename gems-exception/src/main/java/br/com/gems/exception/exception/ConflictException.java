package br.com.gems.exception.exception;

import br.com.gems.exception.exception.enums.ErrorTypeEnum;

import java.util.List;

/**
 * Regra de negócio violada por <b>conflito com o estado atual</b> — 409, não 400.
 * <p>
 * A distinção é a razão de o tipo existir: 400 diz "a requisição está errada, corrija e reenvie";
 * 409 diz "a requisição está certa, mas o estado do recurso não a admite agora" — o último gestor
 * habilitado de uma organização, uma chave que outro registro já ocupa. Reenviar o mesmo corpo não
 * resolve, e o cliente precisa saber disso para não tentar.
 * </p>
 * <p>
 * Estende {@link BusinessException} para que o envelope seja o mesmo: {@code codigo} e
 * {@code detalhes} continuam acompanhando a mensagem (EX-1). Só o status muda.
 * </p>
 */
public class ConflictException extends BusinessException {

    /**
     * Conflito com {@link ErrorTypeEnum#FALHA} e código de domínio.
     * @param message Mensagem amigável que será enviada ao cliente.
     * @param codigo Código de domínio do conflito.
     */
    public ConflictException( String message, String codigo ) {
        this( ErrorTypeEnum.FALHA, message, codigo, null );
    }

    /**
     * Conflito com {@link ErrorTypeEnum#FALHA}, código de domínio e detalhes.
     * @param message Mensagem amigável que será enviada ao cliente.
     * @param codigo Código de domínio do conflito.
     * @param detalhes O que conflitou, item a item, ou {@code null}.
     */
    public ConflictException( String message, String codigo, List<String> detalhes ) {
        this( ErrorTypeEnum.FALHA, message, codigo, detalhes );
    }

    /**
     * Construtor completo.
     * @param errorType Enum que tipifica a natureza do erro.
     * @param message Mensagem amigável que será enviada ao cliente.
     * @param codigo Código de domínio do conflito, ou {@code null}.
     * @param detalhes O que conflitou, item a item, ou {@code null}.
     */
    public ConflictException( ErrorTypeEnum errorType, String message, String codigo, List<String> detalhes ) {
        super( errorType, message, codigo, detalhes );
    }

}
