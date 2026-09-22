package br.com.gems.auditing;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Escuta os eventos do ORM e alimenta a trilha — sem que o domínio saiba que ela existe (AU-1, AU-6).
 * <p>
 * Não é superfície pública: expor o listener congelaria a forma de integração com o Hibernate, que é
 * detalhe de ligação e muda entre versões dele. O consumidor interage por {@link Auditable},
 * {@link AuditActorProvider} e {@link AuditTrailDestination} — nunca por esta classe.
 * </p>
 * <p>
 * <strong>Os eventos são {@code Post}</strong>, não {@code Pre}: a trilha registra o que aconteceu, e
 * num evento anterior ao flush o identificador gerado ainda não existe.
 * </p>
 */
final class HibernateAuditListener
        implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private static final long serialVersionUID = 1L;

    /** O campo que representa a exclusão na trilha, já que o {@code delete} não tem estado novo. */
    private static final String CAMPO_DE_EXCLUSAO = "DT_EXCLUSAO";

    private final transient TransactionalAuditWriter writer;
    private final transient AuditActorProvider actors;
    private final transient AuditContextProvider contexts;
    private final transient AuditTrailDestination destination;

    HibernateAuditListener(TransactionalAuditWriter writer, AuditActorProvider actors,
            AuditContextProvider contexts, AuditTrailDestination destination) {
        this.writer = writer;
        this.actors = actors;
        this.contexts = contexts;
        this.destination = destination;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        List<Integer> todos = IntStream.range(0, event.getState().length).boxed().toList();
        registrar(event.getPersister(), event.getId(), event.getState(), null, todos,
                AuditOperation.INSERT, event.getSession());
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        List<Integer> sujos = event.getDirtyProperties() == null
                ? List.of()
                : Arrays.stream(event.getDirtyProperties()).boxed().toList();
        registrar(event.getPersister(), event.getId(), event.getState(), event.getOldState(), sujos,
                AuditOperation.UPDATE, event.getSession());
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        gravar(event.getPersister(), event.getId(),
                List.of(new AuditChange(CAMPO_DE_EXCLUSAO, null, "EXCLUIDO", false)),
                AuditOperation.DELETE, event.getSession());
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        // false de propósito: tratar depois do commit tiraria a gravação da transação auditada e
        // quebraria AU-5 — a trilha sobreviveria a um rollback da operação que ela descreve.
        return false;
    }

    private void registrar(EntityPersister persister, Object id, Object[] atual, Object[] anterior,
            List<Integer> indices, AuditOperation operation, SharedSessionContractImplementor session) {
        if (!AuditPolicy.isAuditable(persister.getMappedClass())) {
            return;
        }
        gravar(persister, id,
                AuditPolicy.changedFields(persister.getPropertyNames(), anterior, atual, indices,
                        persister.getMappedClass()),
                operation, session);
    }

    private void gravar(EntityPersister persister, Object id, List<AuditChange> changes,
            AuditOperation operation, SharedSessionContractImplementor session) {
        if (changes.isEmpty() || !AuditPolicy.isAuditable(persister.getMappedClass())) {
            return;
        }

        String entidade = persister.getJpaEntityName();
        AuditActor actor = actors.currentActor();
        AuditContext context = contexts.currentContext();
        writer.write(session.getJdbcCoordinator().getLogicalConnection().getPhysicalConnection(),
                destination.schemaFor(entidade), operation, entidade, id, actor, context, changes);
    }
}
