package br.com.gems.auditing;

/**
 * Onde a trilha é gravada — ponto de extensão do consumidor.
 * <p>
 * <strong>A SDK não arbitra o destino, e a razão é concreta.</strong> Os dois consumidores de
 * referência resolveram isso de formas legitimamente diferentes: um grava num schema global, o que
 * torna trivial a consulta cruzada entre organizações; o outro grava no schema da própria organização,
 * o que mantém a trilha isolada junto do dado que ela descreve. Nenhuma das duas é errada — elas
 * respondem a requisitos diferentes, e o módulo que escolhesse por elas estaria decidindo política de
 * retenção e de acesso no lugar de quem responde por ela.
 * </p>
 * <p>
 * O padrão registrado é {@link FixedSchemaAuditTrailDestination}, que exige a propriedade
 * {@code gems.auditing.schema}. Quem precisa rotear por organização implementa esta interface — e é
 * onde o consumidor multi-tenant consulta o contexto dele.
 * </p>
 */
@FunctionalInterface
public interface AuditTrailDestination {

    /**
     * O schema em que a trilha desta entidade é gravada.
     *
     * @param entityName nome da entidade auditada, para quem roteia por domínio.
     * @return o nome do schema; nunca {@code null} nem em branco.
     */
    String schemaFor(String entityName);
}
