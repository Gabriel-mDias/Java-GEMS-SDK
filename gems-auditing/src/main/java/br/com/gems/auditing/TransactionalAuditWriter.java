package br.com.gems.auditing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Grava a trilha na <strong>conexão do chamador</strong>, participando da transação auditada (AU-5).
 * <p>
 * <strong>É por isso que a classe não é pública.</strong> O contrato diz que serviço de domínio nunca
 * chama auditoria diretamente (AU-6), e a diferença entre uma regra escrita e uma regra que vale está
 * em quem a impõe. Sendo visível só neste pacote, quem tenta chamá-la de um serviço não recebe um
 * aviso em revisão de código: recebe um erro de compilação. A trilha entra por evento do ORM, ou não
 * entra.
 * </p>
 * <p>
 * <strong>Não existe {@code @Transactional} aqui, e a ausência é o mecanismo.</strong> A anotação
 * abriria — ou pior, poderia abrir — uma transação própria, e a trilha sobreviveria ao rollback da
 * operação que ela descreve: ficaria registrado que algo mudou quando nada mudou. Escrevendo na
 * {@link Connection} que o chamador já usa, a trilha é desfeita exatamente com o que a originou, sem
 * coordenação nenhuma.
 * </p>
 * <p>
 * A classe <strong>não</strong> conhece {@code DataSource}, e isso não é economia: um
 * {@code DataSource} aqui daria a quem editar esta classe o poder de obter uma conexão nova sem
 * perceber o que está quebrando.
 * </p>
 * <p>
 * As tabelas {@code AUDIT_OPERATION} e {@code AUDIT_CHANGE} são criadas pelo consumidor, na migração
 * dele. Ver o {@code AI-CONSUMER-GUIDE} para o DDL esperado.
 * </p>
 */
final class TransactionalAuditWriter {

    /**
     * Identificador SQL seguro.
     * <p>
     * O nome do schema entra por concatenação — não há como parametrizar identificador em SQL — e vem
     * de {@link AuditTrailDestination}, que é código do consumidor e pode derivá-lo de dado de
     * requisição. Validar aqui é a última barreira antes do banco.
     * </p>
     */
    private static final Pattern SCHEMA_SEGURO = Pattern.compile("^[a-z][a-z0-9_]{2,62}$");

    private static final String INSERT_OPERATION = """
            insert into %s.AUDIT_OPERATION
            (ID_AUDIT_OPERATION,CD_OPERATION,NM_ENTITY,DS_ENTITY_ID,NM_ACTOR,CD_ACTOR_TYPE,DT_OPERATION)
            values (?,?,?,?,?,?,?)""";

    private static final String INSERT_OPERATION_WITH_CONTEXT = """
            insert into %s.AUDIT_OPERATION
            (ID_AUDIT_OPERATION,CD_OPERATION,NM_ENTITY,DS_ENTITY_ID,NM_ACTOR,CD_ACTOR_TYPE,ID_ACTOR,CD_CORRELATION,DT_OPERATION)
            values (?,?,?,?,?,?,?,?,?)""";

    private static final String INSERT_CHANGE = """
            insert into %s.AUDIT_CHANGE
            (ID_AUDIT_CHANGE,ID_AUDIT_OPERATION,NM_FIELD,DS_OLD_VALUE,DS_NEW_VALUE,FL_SIGILOSO)
            values (?,?,?,?,?,?)""";

    private final boolean contextColumnsEnabled;

    TransactionalAuditWriter() {
        this(false);
    }

    TransactionalAuditWriter(boolean contextColumnsEnabled) {
        this.contextColumnsEnabled = contextColumnsEnabled;
    }

    /**
     * Grava uma operação e as mudanças dela.
     *
     * @param connection a conexão da transação auditada. <strong>Não</strong> é fechada aqui: ela
     *                   pertence a quem a abriu.
     */
    void write(Connection connection, String schema, AuditOperation operation, String entity, Object entityId,
            AuditActor actor, List<AuditChange> changes) {
        write(connection, schema, operation, entity, entityId, actor, AuditContext.empty(), changes);
    }

    void write(Connection connection, String schema, AuditOperation operation, String entity, Object entityId,
            AuditActor actor, AuditContext context, List<AuditChange> changes) {
        if (changes.isEmpty()) {
            return;
        }

        String schemaValidado = validar(schema);
        UUID operationId = UUID.randomUUID();
        AuditContext contextValidado = context == null ? AuditContext.empty() : context;

        try (PreparedStatement header = connection.prepareStatement((contextColumnsEnabled
                ? INSERT_OPERATION_WITH_CONTEXT
                : INSERT_OPERATION).formatted(schemaValidado));
                PreparedStatement detail = connection.prepareStatement(INSERT_CHANGE.formatted(schemaValidado))) {

            header.setObject(1, operationId);
            header.setString(2, operation.name());
            header.setString(3, entity);
            header.setString(4, String.valueOf(entityId));
            header.setString(5, actor.name());
            header.setString(6, actor.type().name());
            if (contextColumnsEnabled) {
                header.setString(7, contextValidado.actorId());
                header.setString(8, contextValidado.correlationId());
                header.setTimestamp(9, Timestamp.from(Instant.now()));
            } else {
                header.setTimestamp(7, Timestamp.from(Instant.now()));
            }
            header.executeUpdate();

            for (AuditChange change : changes) {
                detail.setObject(1, UUID.randomUUID());
                detail.setObject(2, operationId);
                detail.setString(3, change.field());
                detail.setString(4, change.oldValue());
                detail.setString(5, change.newValue());
                detail.setBoolean(6, change.sensitive());
                detail.addBatch();
            }
            detail.executeBatch();

        } catch (SQLException excecao) {
            throw new AuditWriteException(entity, excecao);
        }
    }

    private static String validar(String schema) {
        if (schema == null || !SCHEMA_SEGURO.matcher(schema).matches()) {
            throw new IllegalArgumentException("Schema de auditoria inválido: " + schema);
        }
        return schema;
    }
}
