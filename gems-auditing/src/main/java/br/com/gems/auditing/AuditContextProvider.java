package br.com.gems.auditing;

/**
 * De onde vem o contexto complementar da operação auditada.
 * <p>
 * É ponto de extensão porque correlação e identificador de ator pertencem à aplicação consumidora.
 * A implementação não deve abrir transação, consultar banco ou inventar valores. O retorno
 * {@code null} é tratado pelo módulo como {@link AuditContext#empty()}.
 * </p>
 */
@FunctionalInterface
public interface AuditContextProvider {

    /** Obtém o contexto da operação em curso, ou {@code null} quando ele está ausente. */
    AuditContext currentContext();
}
