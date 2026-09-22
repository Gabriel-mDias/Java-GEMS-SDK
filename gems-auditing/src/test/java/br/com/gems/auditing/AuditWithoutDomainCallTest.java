package br.com.gems.auditing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AU-1: a alteração de uma entidade auditável gera trilha <strong>sem que o domínio chame nada</strong>.
 * <p>
 * O teste sobe um Hibernate de verdade sobre H2, com o integrador do módulo registrado, e persiste
 * entidades por {@code session.persist(...)} — a mesma chamada que um serviço de domínio faria. Nenhum
 * tipo de auditoria aparece no caminho de escrita. Se a trilha for gravada assim, ela é gravada por
 * evento do ORM, que é exatamente o que AU-1 afirma.
 * </p>
 * <p>
 * Um teste que chamasse o escritor diretamente provaria que o escritor escreve — nunca que o domínio
 * não precisa saber dele. Por isso o bootstrap real: é o único jeito de a afirmação ser verificada em
 * vez de assumida.
 * </p>
 */
class AuditWithoutDomainCallTest {

    private static final String SCHEMA = "auditoria";

    private String url;
    private SessionFactory sessionFactory;
    private AtomicInteger contextCalls;

    @BeforeEach
    void prepararBancoESessionFactory() throws SQLException {
        url = "jdbc:h2:mem:sem-chamada-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        criarTabelasDaTrilha();

        contextCalls = new AtomicInteger();
        HibernateAuditListener listener = new HibernateAuditListener(new TransactionalAuditWriter(true),
                new SystemAuditActorProvider(), () -> {
                    contextCalls.incrementAndGet();
                    return new AuditContext("usuario-42", "correlacao-42");
                }, entidade -> SCHEMA);

        BootstrapServiceRegistry bootstrap = new BootstrapServiceRegistryBuilder()
                .applyIntegrator(new AuditHibernateIntegrator(listener))
                .build();

        StandardServiceRegistry registro = new StandardServiceRegistryBuilder(bootstrap)
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "create")
                .build();

        sessionFactory = new MetadataSources(registro)
                .addAnnotatedClass(MatriculaDeTeste.class)
                .addAnnotatedClass(ParametroDeTeste.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @AfterEach
    void fechar() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @Test
    @DisplayName("AU-1: persistir entidade auditável grava a trilha sem nenhuma chamada do domínio")
    void insercaoGeraTrilhaSemChamadaDoDominio() throws SQLException {
        sessionFactory.inTransaction(session -> {
            MatriculaDeTeste matricula = new MatriculaDeTeste();
            matricula.id = 1L;
            matricula.situacao = "ATIVA";
            session.persist(matricula);
        });

        assertThat(contar("AUDIT_OPERATION", "CD_OPERATION = 'INSERT'"))
                .describedAs("a trilha precisa nascer do evento do ORM — o bloco acima não menciona auditoria")
                .isEqualTo(1);
        assertThat(valorUnico("select NM_ENTITY from " + SCHEMA + ".AUDIT_OPERATION"))
                .isEqualTo(MatriculaDeTeste.class.getSimpleName());
    }

    @Test
    @DisplayName("O listener leva o contexto do provider ao cabeçalho da mesma operação ORM")
    void insercaoGravaContextoDoProvider() throws SQLException {
        sessionFactory.inTransaction(session -> {
            MatriculaDeTeste matricula = new MatriculaDeTeste();
            matricula.id = 3L;
            matricula.situacao = "ATIVA";
            session.persist(matricula);
        });

        assertThat(valorUnico("select ID_ACTOR from " + SCHEMA + ".AUDIT_OPERATION"))
                .isEqualTo("usuario-42");
        assertThat(valorUnico("select CD_CORRELATION from " + SCHEMA + ".AUDIT_OPERATION"))
                .isEqualTo("correlacao-42");
        assertThat(contextCalls).hasValue(1);
    }

    @Test
    @DisplayName("AU-1: alterar um campo grava a mudança, com o valor anterior e o novo")
    void alteracaoGravaOCampoMudado() throws SQLException {
        sessionFactory.inTransaction(session -> {
            MatriculaDeTeste matricula = new MatriculaDeTeste();
            matricula.id = 2L;
            matricula.situacao = "ATIVA";
            session.persist(matricula);
        });

        sessionFactory.inTransaction(session -> session.find(MatriculaDeTeste.class, 2L).situacao = "TRANCADA");

        assertThat(contar("AUDIT_OPERATION", "CD_OPERATION = 'UPDATE'")).isEqualTo(1);
        try (Connection conexao = abrir();
                Statement comando = conexao.createStatement();
                ResultSet resultado = comando.executeQuery("select NM_FIELD, DS_OLD_VALUE, DS_NEW_VALUE from "
                        + SCHEMA + ".AUDIT_CHANGE where NM_FIELD = 'situacao' and DS_NEW_VALUE = 'TRANCADA'")) {
            assertThat(resultado.next()).isTrue();
            assertThat(resultado.getString("DS_OLD_VALUE")).isEqualTo("ATIVA");
            assertThat(resultado.getString("DS_NEW_VALUE")).isEqualTo("TRANCADA");
        }
    }

    @Test
    @DisplayName("A trilha é opt-in: entidade sem @Auditable não gera registro algum")
    void entidadeNaoMarcadaFicaForaDaTrilha() throws SQLException {
        sessionFactory.inTransaction(session -> {
            ParametroDeTeste parametro = new ParametroDeTeste();
            parametro.id = 1L;
            parametro.valor = "30";
            session.persist(parametro);
        });

        assertThat(contar("AUDIT_OPERATION", "1 = 1"))
                .describedAs("sem o marcador, a entidade sai da trilha — em silêncio, e é o esperado")
                .isZero();
    }

    @Test
    @DisplayName("A entidade auditada não conhece nenhum tipo de auditoria além do marcador")
    void entidadeAuditadaNaoDependeDoModulo() {
        assertThat(MatriculaDeTeste.class.isAnnotationPresent(Auditable.class)).isTrue();

        for (Field campo : MatriculaDeTeste.class.getDeclaredFields()) {
            assertThat(campo.getType().getPackageName())
                    .describedAs("campo %s não pode referenciar o módulo de auditoria", campo.getName())
                    .isNotEqualTo(Auditable.class.getPackageName());
            assertThat(anotacoesDeAuditoria(campo)).isZero();
        }
        for (Method metodo : MatriculaDeTeste.class.getDeclaredMethods()) {
            assertThat(metodo.getReturnType().getPackageName())
                    .describedAs("método %s não pode devolver tipo de auditoria", metodo.getName())
                    .isNotEqualTo(Auditable.class.getPackageName());
        }
    }

    private static long anotacoesDeAuditoria(AnnotatedElement elemento) {
        return java.util.Arrays.stream(elemento.getAnnotations())
                .filter(anotacao -> anotacao.annotationType().getPackageName().equals(Auditable.class.getPackageName()))
                .count();
    }

    /**
     * O mesmo usuário que o Hibernate usa. Abrir o banco em memória com credencial diferente criaria
     * um segundo banco, e o teste passaria a olhar um banco onde nada aconteceu.
     */
    private Connection abrir() throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    private void criarTabelasDaTrilha() throws SQLException {
        try (Connection conexao = abrir();
                Statement comando = conexao.createStatement()) {
            comando.execute("create schema if not exists " + SCHEMA);
            comando.execute("""
                    create table %s.AUDIT_OPERATION (
                        ID_AUDIT_OPERATION uuid primary key,
                        CD_OPERATION varchar(20) not null,
                        NM_ENTITY varchar(200) not null,
                        DS_ENTITY_ID varchar(200),
                        NM_ACTOR varchar(200) not null,
                        CD_ACTOR_TYPE varchar(20) not null,
                        ID_ACTOR varchar(200),
                        CD_CORRELATION varchar(200),
                        DT_OPERATION timestamp not null)""".formatted(SCHEMA));
            comando.execute("""
                    create table %s.AUDIT_CHANGE (
                        ID_AUDIT_CHANGE uuid primary key,
                        ID_AUDIT_OPERATION uuid not null,
                        NM_FIELD varchar(200) not null,
                        DS_OLD_VALUE varchar(4000),
                        DS_NEW_VALUE varchar(4000),
                        FL_SIGILOSO boolean not null)""".formatted(SCHEMA));
        }
    }

    private int contar(String tabela, String filtro) throws SQLException {
        try (Connection conexao = abrir();
                Statement comando = conexao.createStatement();
                ResultSet resultado = comando.executeQuery(
                        "select count(*) from " + SCHEMA + "." + tabela + " where " + filtro)) {
            resultado.next();
            return resultado.getInt(1);
        }
    }

    private String valorUnico(String consulta) throws SQLException {
        try (Connection conexao = abrir();
                Statement comando = conexao.createStatement();
                ResultSet resultado = comando.executeQuery(consulta)) {
            resultado.next();
            return resultado.getString(1);
        }
    }

    /** Entidade de domínio marcada para a trilha. Não conhece o módulo além da anotação. */
    @Auditable
    @Entity(name = "MatriculaDeTeste")
    static class MatriculaDeTeste {

        @Id
        Long id;

        String situacao;
    }

    /** Entidade de infraestrutura, deliberadamente fora da trilha. */
    @Entity(name = "ParametroDeTeste")
    static class ParametroDeTeste {

        @Id
        Long id;

        String valor;
    }
}
