package br.com.gems.auditing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AU-5: o registro é desfeito junto com a transação que o originou.
 * <p>
 * Este teste usa um banco real em memória, e não dublês, porque a afirmação que ele verifica é sobre o
 * <strong>banco</strong>: que as linhas somem no rollback. Com um dublê de {@link Connection}, o teste
 * provaria apenas que certos métodos foram chamados — e a forma de quebrar AU-5 (o escritor abrir uma
 * conexão própria) continuaria passando, porque as chamadas na conexão dublê seriam as mesmas.
 * </p>
 */
class TransactionalAuditWriterTest {

    private static final String SCHEMA = "auditoria";

    private Connection connection;
    private TransactionalAuditWriter writer;

    @BeforeEach
    void prepararBanco() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:trilha-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("create schema if not exists " + SCHEMA);
            statement.execute("""
                    create table %s.AUDIT_OPERATION (
                        ID_AUDIT_OPERATION uuid primary key,
                        CD_OPERATION varchar(20) not null,
                        NM_ENTITY varchar(200) not null,
                        DS_ENTITY_ID varchar(200),
                        NM_ACTOR varchar(200) not null,
                        CD_ACTOR_TYPE varchar(20) not null,
                        DT_OPERATION timestamp not null)""".formatted(SCHEMA));
            statement.execute("""
                    create table %s.AUDIT_CHANGE (
                        ID_AUDIT_CHANGE uuid primary key,
                        ID_AUDIT_OPERATION uuid not null,
                        NM_FIELD varchar(200) not null,
                        DS_OLD_VALUE varchar(4000),
                        DS_NEW_VALUE varchar(4000),
                        FL_SIGILOSO boolean not null)""".formatted(SCHEMA));
        }
        connection.commit();
        writer = new TransactionalAuditWriter();
    }

    @AfterEach
    void fecharBanco() throws SQLException {
        connection.close();
    }

    @Test
    @DisplayName("AU-5: rollback da transação desfaz o registro da trilha")
    void rollbackDesfazORegistro() throws SQLException {
        writer.write(connection, SCHEMA, AuditOperation.UPDATE, "Matricula", 42, AuditActor.tecnico(),
                List.of(new AuditChange("situacao", "ATIVA", "TRANCADA", false)));

        assertThat(contar("AUDIT_OPERATION"))
                .describedAs("antes do rollback, a trilha está gravada na mesma transação")
                .isEqualTo(1);

        connection.rollback();

        assertThat(contar("AUDIT_OPERATION"))
                .describedAs("a trilha não pode sobreviver à operação que ela descreve — senão registra "
                        + "que algo mudou quando nada mudou")
                .isZero();
        assertThat(contar("AUDIT_CHANGE")).isZero();
    }

    @Test
    @DisplayName("O commit da transação auditada persiste a trilha junto")
    void commitPersisteATrilhaJunto() throws SQLException {
        writer.write(connection, SCHEMA, AuditOperation.INSERT, "Matricula", 42, AuditActor.usuario("ada"),
                List.of(new AuditChange("situacao", null, "ATIVA", false)));

        connection.commit();

        assertThat(contar("AUDIT_OPERATION")).isEqualTo(1);
        assertThat(contar("AUDIT_CHANGE")).isEqualTo(1);
    }

    @Test
    @DisplayName("Com opt-in, ator e correlação entram no mesmo cabeçalho da operação")
    void optInGravaContextoNoCabecalhoDaOperacao() throws SQLException {
        adicionarColunasDeContexto();
        TransactionalAuditWriter writerComContexto = new TransactionalAuditWriter(true);

        writerComContexto.write(connection, SCHEMA, AuditOperation.INSERT, "Matricula", 42,
                AuditActor.usuario("ada"), new AuditContext("usuario-42", "correlacao-42"),
                List.of(new AuditChange("situacao", null, "ATIVA", false)));
        connection.commit();

        try (Statement statement = connection.createStatement();
                ResultSet resultado = statement.executeQuery(
                        "select ID_ACTOR, CD_CORRELATION from " + SCHEMA + ".AUDIT_OPERATION")) {
            assertThat(resultado.next()).isTrue();
            assertThat(resultado.getString("ID_ACTOR")).isEqualTo("usuario-42");
            assertThat(resultado.getString("CD_CORRELATION")).isEqualTo("correlacao-42");
        }
    }

    @Test
    @DisplayName("Opt-in sobre schema sem colunas de contexto falha como escrita de auditoria")
    void optInEmSchemaSemColunasFalhaComoAuditWriteException() {
        TransactionalAuditWriter writerComContexto = new TransactionalAuditWriter(true);

        assertThatThrownBy(() -> writerComContexto.write(connection, SCHEMA, AuditOperation.INSERT,
                "Matricula", 42, AuditActor.tecnico(), AuditContext.empty(),
                List.of(new AuditChange("situacao", null, "ATIVA", false))))
                .isInstanceOf(AuditWriteException.class)
                .hasMessageContaining("Matricula");
    }

    @Test
    @DisplayName("Rollback remove cabeçalho, mudanças e contexto juntos")
    void rollbackRemoveContextoJuntoComATrilha() throws SQLException {
        adicionarColunasDeContexto();
        TransactionalAuditWriter writerComContexto = new TransactionalAuditWriter(true);

        writerComContexto.write(connection, SCHEMA, AuditOperation.UPDATE, "Matricula", 42,
                AuditActor.usuario("ada"), new AuditContext("usuario-42", "correlacao-42"),
                List.of(new AuditChange("situacao", "ATIVA", "TRANCADA", false)));

        connection.rollback();

        assertThat(contar("AUDIT_OPERATION")).isZero();
        assertThat(contar("AUDIT_CHANGE")).isZero();
    }

    @Test
    @DisplayName("AU-4: o campo sigiloso chega ao banco sem os valores, mas com o registro da mudança")
    void campoSigilosoChegaSemValores() throws SQLException {
        writer.write(connection, SCHEMA, AuditOperation.UPDATE, "Usuario", 7, AuditActor.usuario("ada"),
                List.of(new AuditChange("senha", "antiga", "nova", true)));
        connection.commit();

        try (Statement statement = connection.createStatement();
                ResultSet resultado = statement.executeQuery(
                        "select NM_FIELD, DS_OLD_VALUE, DS_NEW_VALUE, FL_SIGILOSO from " + SCHEMA + ".AUDIT_CHANGE")) {
            assertThat(resultado.next()).isTrue();
            assertThat(resultado.getString("NM_FIELD")).isEqualTo("senha");
            assertThat(resultado.getString("DS_OLD_VALUE")).isNull();
            assertThat(resultado.getString("DS_NEW_VALUE")).isNull();
            assertThat(resultado.getBoolean("FL_SIGILOSO")).isTrue();
        }
    }

    @Test
    @DisplayName("AU-2 e AU-3: o tipo do autor é gravado ao lado do nome")
    void tipoDoAutorEhGravado() throws SQLException {
        writer.write(connection, SCHEMA, AuditOperation.INSERT, "Matricula", 1, AuditActor.tecnico(),
                List.of(new AuditChange("situacao", null, "ATIVA", false)));
        connection.commit();

        try (Statement statement = connection.createStatement();
                ResultSet resultado = statement.executeQuery(
                        "select NM_ACTOR, CD_ACTOR_TYPE from " + SCHEMA + ".AUDIT_OPERATION")) {
            assertThat(resultado.next()).isTrue();
            assertThat(resultado.getString("NM_ACTOR")).isEqualTo("SISTEMA");
            assertThat(resultado.getString("CD_ACTOR_TYPE")).isEqualTo("TECNICO");
        }
    }

    @Test
    @DisplayName("Sem mudança alguma, nada é gravado — trilha vazia não é registro")
    void semMudancaNadaEhGravado() throws SQLException {
        writer.write(connection, SCHEMA, AuditOperation.UPDATE, "Matricula", 42, AuditActor.tecnico(), List.of());
        connection.commit();

        assertThat(contar("AUDIT_OPERATION")).isZero();
    }

    @Test
    @DisplayName("Schema fora do formato de identificador é recusado antes de chegar ao banco")
    void schemaInvalidoEhRecusado() {
        assertThatThrownBy(() -> writer.write(connection, "auditoria; drop table alunos", AuditOperation.INSERT,
                "Matricula", 1, AuditActor.tecnico(), List.of(new AuditChange("a", null, "b", false))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schema de auditoria inválido");
    }

    @Test
    @DisplayName("Falha de gravação sobe como AuditWriteException, e não em silêncio")
    void falhaDeGravacaoSobe() {
        assertThatThrownBy(() -> writer.write(connection, "inexistente", AuditOperation.INSERT, "Matricula", 1,
                AuditActor.tecnico(), List.of(new AuditChange("a", null, "b", false))))
                .isInstanceOf(AuditWriteException.class)
                .hasMessageContaining("Matricula");
    }

    private int contar(String tabela) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultado = statement.executeQuery("select count(*) from " + SCHEMA + "." + tabela)) {
            resultado.next();
            return resultado.getInt(1);
        }
    }

    private void adicionarColunasDeContexto() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table " + SCHEMA + ".AUDIT_OPERATION add ID_ACTOR varchar(200)");
            statement.execute("alter table " + SCHEMA + ".AUDIT_OPERATION add CD_CORRELATION varchar(200)");
        }
        connection.commit();
    }
}
