package br.com.gems.tenant.service;

import br.com.gems.tenant.SchemaInspector;
import br.com.gems.tenant.TenantSchemaNaming;
import br.com.gems.tenant.migration.TenantSchemaMigrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * O ponto de provisionamento: cria o schema de uma organização nova e o migra.
 * <p>
 * <strong>É aqui — e no arranque — que migração acontece.</strong> O caminho de persistência nunca
 * migra: ele verifica e recusa (MT-3). Esta separação é o que mantém o privilégio de DDL fora do
 * caminho de requisição e elimina a corrida entre dois requests que encontrem o mesmo schema faltando.
 * </p>
 * <p>
 * <strong>Duas correções nesta rodada.</strong> O nome do schema vem de {@link TenantSchemaNaming},
 * não de um {@code @Value} próprio — antes esta classe usava {@code instituicao_} enquanto a
 * configuração do Liquibase usava {@code client_tenant_}, e o efeito era migrar num schema e ler de
 * outro. E a criação do schema e a migração passam a delegar a colaboradores testáveis, em vez de
 * montar SQL e Liquibase à mão aqui dentro.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class TenantSchemaService {

    private final TenantSchemaNaming naming;
    private final SchemaInspector inspector;
    private final TenantSchemaMigrator migrator;

    /**
     * Provisiona o schema de uma organização: cria se não existir, e migra.
     * <p>
     * Idempotente de propósito — reexecutar o provisionamento de uma organização que já existe aplica
     * apenas as migrações pendentes. Falha de migração <strong>lança</strong>: um schema meio migrado
     * que se declara pronto é pior do que um provisionamento que falhou visivelmente.
     * </p>
     *
     * <p>
     * O nome do método é o que sempre foi. A superfície pública deste módulo muda apenas nos pontos que
     * o contrato da rodada enumera, e este não é um deles — renomear aqui seria mudança gratuita, e
     * ainda por cima em desacordo com o restante do módulo, que nomeia métodos em inglês.
     * </p>
     *
     * @param alias a sigla da organização.
     * @return o nome do schema provisionado.
     */
    public String createSchemaAndRunLiquibase(String alias) {
        String schema = naming.schemaFor(alias);
        log.info("event=TENANT_PROVISIONING_STARTED organizacao={} modulo=gems-jpa-multi-tenant", schema);

        if (!inspector.exists(schema)) {
            inspector.create(schema);
        }
        migrator.migrate(schema);

        return schema;
    }
}
