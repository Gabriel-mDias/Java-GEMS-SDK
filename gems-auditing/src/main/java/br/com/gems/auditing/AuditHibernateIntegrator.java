package br.com.gems.auditing;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;

/**
 * Liga o listener ao Hibernate no arranque da {@code SessionFactory}.
 * <p>
 * Detalhe de ligação, não superfície pública — pelo mesmo motivo que o listener.
 * </p>
 */
final class AuditHibernateIntegrator implements Integrator {

    private final HibernateAuditListener listener;

    AuditHibernateIntegrator(HibernateAuditListener listener) {
        this.listener = listener;
    }

    @Override
    public void integrate(Metadata metadata, BootstrapContext bootstrapContext, SessionFactoryImplementor factory) {
        EventListenerRegistry registry = factory.getServiceRegistry().getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_INSERT, listener);
        registry.appendListeners(EventType.POST_UPDATE, listener);
        registry.appendListeners(EventType.POST_DELETE, listener);
    }
}
