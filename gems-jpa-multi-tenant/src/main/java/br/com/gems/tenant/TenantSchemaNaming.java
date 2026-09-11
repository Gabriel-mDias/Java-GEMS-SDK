package br.com.gems.tenant;

/**
 * A <strong>única</strong> fonte do nome de schema deste módulo.
 * <p>
 * Antes desta rodada, {@code gems.tenant.schema-prefix} era lido em três lugares com
 * <strong>dois padrões diferentes</strong>: {@code instituicao_} no provedor de conexão e no serviço de
 * schema, {@code client_tenant_} na configuração do Liquibase. O efeito é o pior possível de se
 * diagnosticar: a migração roda num schema e o tráfego lê de outro, cada um convencido de estar certo,
 * e nenhum erro é emitido em lugar algum. A aplicação simplesmente não encontra os dados que acabou de
 * gravar.
 * </p>
 * <p>
 * Por isso o prefixo passa a viver aqui e em nenhum outro lugar. Um colaborador que volte a declarar o
 * próprio {@code @Value} para esta propriedade reintroduz o defeito, e é exatamente isso que
 * {@code SchemaNamingConsistencyTest} procura — não basta que os valores coincidam hoje.
 * </p>
 * <p>
 * O padrão é {@code tenant_}: é o que o consumidor documenta como sua realidade, e os dois padrões
 * anteriores não tinham consumidor algum para preservar.
 * </p>
 */
public class TenantSchemaNaming {

    /** O padrão único. Alterá-lo é mudança de comportamento publicado, não ajuste de configuração. */
    public static final String DEFAULT_SCHEMA_PREFIX = "tenant_";

    private final String schemaPrefix;
    private final String globalSchema;

    public TenantSchemaNaming(String schemaPrefix, String globalSchema) {
        this.schemaPrefix = schemaPrefix;
        this.globalSchema = globalSchema;
    }

    /**
     * O nome de schema de uma organização, a partir do alias dela.
     *
     * @param alias sigla da organização; validada por {@link TenantIdentifierValidator}, porque o
     *              resultado entra em comandos SQL de DDL e de troca de schema.
     */
    public String schemaFor(String alias) {
        return schemaPrefix + TenantIdentifierValidator.sanitize(alias);
    }

    /** O prefixo em vigor. Exposto para o inspetor listar os schemas deste módulo, não para compor nomes à mão. */
    public String schemaPrefix() {
        return schemaPrefix;
    }

    /**
     * O schema de dado que não pertence a organização alguma.
     * <p>
     * <strong>Sem padrão embutido, de propósito.</strong> Adivinhar um nome aqui rotearia o tráfego
     * global de quem esqueceu de configurar para um schema que existe por acaso. Quem não usa escopo
     * global nunca chama este método e nunca precisa da propriedade; quem usa, declara.
     * </p>
     *
     * @throws IllegalStateException se {@code gems.tenant.global-schema} não estiver configurada.
     */
    public String globalSchema() {
        if (globalSchema == null || globalSchema.isBlank()) {
            throw new IllegalStateException(
                    "Escopo global pedido sem gems.tenant.global-schema configurada. "
                            + "O schema global não tem padrão: declare-o ou não use TenantScope.global().");
        }
        return globalSchema;
    }
}
