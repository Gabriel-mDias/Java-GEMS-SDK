package br.com.gems.tenant;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-016 e SC-011: a recusa de MT-1 e o conflito de MT-8 emitem log estruturado.
 * <p>
 * O teste captura os eventos por {@link ListAppender} em vez de olhar a saída de console, porque o que
 * está sendo verificado é o <strong>esquema</strong> do evento: nível e campos. Sem isso, "tem log"
 * viraria uma afirmação sobre alguma linha existir em algum lugar.
 * </p>
 * <p>
 * É o alvo da mutação T108: remover a emissão de MT-1 tem de deixar este teste vermelho.
 * </p>
 */
class TenantLoggingTest {

    private ListAppender<ILoggingEvent> eventos;
    private Logger loggerDoContexto;
    private Logger loggerDoResolvedor;

    @BeforeEach
    void capturarLogs() {
        JpaTenantContext.clear();
        eventos = new ListAppender<>();
        eventos.start();
        loggerDoContexto = (Logger) LoggerFactory.getLogger(JpaTenantContext.class);
        loggerDoResolvedor = (Logger) LoggerFactory.getLogger(TenantAliasResolver.class);
        loggerDoContexto.addAppender(eventos);
        loggerDoResolvedor.addAppender(eventos);
    }

    @AfterEach
    void soltarLogs() {
        loggerDoContexto.detachAppender(eventos);
        loggerDoResolvedor.detachAppender(eventos);
        JpaTenantContext.clear();
    }

    @Test
    @DisplayName("A recusa de MT-1 emite ERROR com a organização sentinela e a causa")
    void recusaDeContextoEmiteLogEstruturado() {
        assertThatThrownBy(JpaTenantContext::getCurrentTenant)
                .isInstanceOf(TenantContextMissingException.class);

        ILoggingEvent evento = unicoEvento();
        assertThat(evento.getLevel())
                .describedAs("chegar ao banco sem escopo é defeito do consumidor; em WARN passaria "
                        + "despercebido em ambiente que só alerta em ERROR")
                .isEqualTo(Level.ERROR);
        assertThat(evento.getFormattedMessage())
                .contains("event=TENANT_CONTEXT_MISSING")
                .contains("organizacao=" + TenantLogFields.ORGANIZACAO_AUSENTE)
                .contains("modulo=gems-jpa-multi-tenant")
                .contains("causa=");
    }

    @Test
    @DisplayName("O campo organizacao nunca é omitido — é o que mantém a consulta por campo utilizável")
    void oCampoOrganizacaoNuncaEhOmitido() {
        assertThatThrownBy(JpaTenantContext::getCurrentTenant)
                .isInstanceOf(TenantContextMissingException.class);

        // A tentação é omitir o campo quando não há organização. Omitir derrota o esquema: a consulta
        // que procura recusas por campo deixa de encontrá-las justamente nos casos em que a
        // organização é desconhecida — que são todos os casos de MT-1.
        assertThat(unicoEvento().getFormattedMessage()).contains("organizacao=<ausente>");
    }

    @Test
    @DisplayName("O conflito de MT-8 emite ERROR nomeando as fontes divergentes")
    void conflitoDeAliasEmiteLogEstruturado() {
        TenantAliasResolver resolver = new TenantAliasResolver(List.of(
                new FonteFixa("ClaimDoJwt", "acme"),
                new FonteFixa("Subdominio", "globex")));

        assertThatThrownBy(resolver::resolve).isInstanceOf(TenantAliasConflictException.class);

        ILoggingEvent evento = unicoEvento();
        assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
        assertThat(evento.getFormattedMessage())
                .contains("event=TENANT_ALIAS_CONFLICT")
                .contains("ClaimDoJwt=acme")
                .contains("Subdominio=globex");
    }

    @Test
    @DisplayName("A falha de migração emite ERROR com o schema e a causa")
    void falhaDeMigracaoEmiteLogEstruturado() throws java.sql.SQLException {
        Logger loggerDoMigrador = (Logger) LoggerFactory
                .getLogger(br.com.gems.tenant.migration.TenantSchemaMigrator.class);
        loggerDoMigrador.addAppender(eventos);
        try {
            // DataSource que recusa conexão: é o caminho de falha de migração, e o Liquibase o
            // converte numa das suas exceções — que é o que o migrador traduz e registra.
            javax.sql.DataSource dataSource = org.mockito.Mockito.mock(javax.sql.DataSource.class);
            org.mockito.Mockito.when(dataSource.getConnection())
                    .thenThrow(new java.sql.SQLException("banco indisponível"));

            var migrador = new br.com.gems.tenant.migration.TenantSchemaMigrator(
                    dataSource, new org.springframework.core.io.DefaultResourceLoader(),
                    "classpath:/db/changelog/inexistente.xml");

            assertThatThrownBy(() -> migrador.migrate("tenant_acme"))
                    .isInstanceOf(br.com.gems.tenant.migration.TenantMigrationException.class);

            assertThat(eventos.list)
                    .describedAs("falha de migração sem log deixa o operador sem a causa")
                    .anySatisfy(evento -> {
                        assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
                        assertThat(evento.getFormattedMessage())
                                .contains("event=TENANT_MIGRATION_FAILED")
                                .contains("organizacao=tenant_acme");
                    });
        } finally {
            loggerDoMigrador.detachAppender(eventos);
        }
    }

    private ILoggingEvent unicoEvento() {
        assertThat(eventos.list).hasSize(1);
        return eventos.list.get(0);
    }

    private record FonteFixa(String nome, String alias) implements TenantAliasSource {

        @Override
        public java.util.Optional<String> currentAlias() {
            return java.util.Optional.ofNullable(alias);
        }

        @Override
        public String sourceName() {
            return nome;
        }
    }
}
