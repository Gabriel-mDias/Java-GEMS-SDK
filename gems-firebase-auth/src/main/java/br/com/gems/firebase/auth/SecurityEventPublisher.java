package br.com.gems.firebase.auth;

/** Porta de publicação de eventos sanitizados após commit; a outbox SQL é a fonte durável. */
@FunctionalInterface
public interface SecurityEventPublisher extends SecurityEventSink {
}
