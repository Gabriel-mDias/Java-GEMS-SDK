package br.com.gems.auditing;

/**
 * De onde vem o autor da alteração — ponto de extensão do consumidor.
 * <p>
 * É interface porque a origem do autor varia: o nome no {@code SecurityContext}, um claim específico do
 * token, o usuário do banco numa integração legada, ou um identificador de job em processamento em
 * lote. A SDK não tem como escolher por quem a consome, e amarrar ao Spring Security obrigaria quem não
 * o usa a contornar o módulo.
 * </p>
 * <p>
 * O padrão registrado é {@link SystemAuditActorProvider}, que devolve sempre o autor técnico. Não é um
 * comportamento útil em produção — é um padrão <em>honesto</em>: sem implementação do consumidor, o
 * módulo registra "não sei quem foi" em vez de inventar um nome.
 * </p>
 */
@FunctionalInterface
public interface AuditActorProvider {

    /**
     * O autor da alteração em curso.
     * <p>
     * <strong>Nunca devolve {@code null} e nunca lança</strong> (AU-3). Uma implementação que falhe ao
     * resolver o autor devolve {@link AuditActor#tecnico()}: a alternativa é derrubar a operação de
     * negócio por causa da trilha, o que inverte a ordem de importância entre as duas.
     * </p>
     */
    AuditActor currentActor();
}
