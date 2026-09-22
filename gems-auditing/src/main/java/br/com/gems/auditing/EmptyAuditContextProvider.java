package br.com.gems.auditing;

/**
 * O provider seguro usado quando a aplicação não fornece contexto complementar.
 * <p>
 * Ele declara a ausência de ator e correlação em vez de deduzi-los de infraestrutura que o módulo não
 * controla.
 * </p>
 */
public class EmptyAuditContextProvider implements AuditContextProvider {

    @Override
    public AuditContext currentContext() {
        return AuditContext.empty();
    }
}
