package br.com.gems.firebase.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Publisher padrão sanitizado, com argumentos estruturados e sem MDC ou exceção remota. */
public final class LoggingSecurityEventPublisher implements SecurityEventPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingSecurityEventPublisher.class);
    @Override public void emit(SecurityEventSink.Event event) {
        var builder = event.type().contains("REFUSED") ? LOG.atError() : LOG.atInfo();
        builder.addKeyValue("organizacao", event.tenantId() == null ? "<ausente>" : event.tenantId())
                .addKeyValue("causa", event.type()).addKeyValue("modulo", "gems-firebase-auth")
                .addKeyValue("evento", event.id()).addKeyValue("usuario", event.userId())
                .addKeyValue("ator", event.actorUserId()).addKeyValue("tipoAlvo", event.targetType())
                .addKeyValue("alvo", event.targetId()).addKeyValue("origem", event.sourceId())
                .addKeyValue("relacao", event.relatedId()).addKeyValue("acao", event.action())
                .addKeyValue("correlacao", event.correlationId()).addKeyValue("fonteAlias", "request-context")
                .addKeyValue("pontoRecusa", event.targetType()).log("Evento de segurança");
    }
}
