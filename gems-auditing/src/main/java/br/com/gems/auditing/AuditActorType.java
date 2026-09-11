package br.com.gems.auditing;

/** A natureza da origem de uma alteração auditada. */
public enum AuditActorType {

    /** Um usuário autenticado. */
    USUARIO,

    /** Um caminho sem usuário: migração, rotina agendada, consumidor de fila, provisionamento. */
    TECNICO
}
