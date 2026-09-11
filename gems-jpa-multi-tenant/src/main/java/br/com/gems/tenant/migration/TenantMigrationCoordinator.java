package br.com.gems.tenant.migration;

import br.com.gems.tenant.SchemaInspector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

/**
 * Migra, no arranque, todos os schemas que {@link TenantSchemaSource} declara — e impede a aplicação de
 * subir se algum falhar.
 * <p>
 * Promovido de {@code meduc-deploy/persistence/tenant/TenantMigrationCoordinator}.
 * </p>
 * <p>
 * <strong>Por que no arranque, e não no primeiro uso.</strong> O contrato desta rodada chegou a prever
 * migração sob demanda, no primeiro acesso a um schema não migrado. Ao desenhar, isso mostrou três
 * problemas: a aplicação precisaria de privilégio de DDL no caminho de requisição; o primeiro request
 * de cada organização pagaria a latência da migração inteira; e dois requests simultâneos sobre o mesmo
 * schema faltante correriam entre si, sem lock que os coordenasse. Migrar aqui — e no ato de
 * provisionamento, para organização nova — elimina os três. No caminho de requisição, o que existe é
 * {@link SchemaInspector#requireReady(String)}: verifica e recusa.
 * </p>
 * <p>
 * Schema esperado e ausente <strong>interrompe o arranque</strong>. É falha de provisionamento, e criar
 * o schema aqui esconderia a causa; a aplicação subiria com um schema vazio, e a organização passaria a
 * ver um sistema sem os dados dela em vez de um erro. Schema órfão — existe no banco e ninguém o espera
 * — apenas avisa: pode ser resíduo de organização removida, e não é motivo para derrubar o arranque.
 * </p>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantMigrationCoordinator implements ApplicationRunner {

    private final TenantSchemaSource source;
    private final SchemaInspector schemas;
    private final TenantSchemaMigrator migrator;

    public TenantMigrationCoordinator(TenantSchemaSource source, SchemaInspector schemas,
            TenantSchemaMigrator migrator) {
        this.source = source;
        this.schemas = schemas;
        this.migrator = migrator;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant inicio = Instant.now();
        Set<String> esperados = new TreeSet<>(source.expectedSchemas());

        schemas.tenantSchemas().stream()
                .filter(schema -> !esperados.contains(schema))
                .sorted()
                .forEach(schema -> log.warn(
                        "event=TENANT_SCHEMA_ORPHAN organizacao={} causa=schema sem registro correspondente "
                                + "modulo=gems-jpa-multi-tenant", schema));

        for (String schema : esperados) {
            schemas.requireReady(schema);
            migrator.migrate(schema);
        }

        log.info("event=TENANT_MIGRATION_BATCH_COMPLETED tenants={} durationMs={} modulo=gems-jpa-multi-tenant",
                esperados.size(), Duration.between(inicio, Instant.now()).toMillis());
    }
}
