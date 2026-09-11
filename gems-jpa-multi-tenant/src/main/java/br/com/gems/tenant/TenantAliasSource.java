package br.com.gems.tenant;

import java.util.Optional;

/**
 * De onde vem o alias da organização (arbitragem A4).
 * <p>
 * O ponto de haver interface é que a origem do alias <strong>não</strong> é o mecanismo. Um claim de
 * JWT é o caso mais comum, não o único: há alias vindo de subdomínio, de cabeçalho em integração
 * servidor-a-servidor, de argumento de linha de comando num job, ou fixado em teste. Amarrar o módulo
 * ao JWT obrigaria todo consumidor que não usa JWT a contornar o módulo.
 * </p>
 * <p>
 * Implementações devolvem {@link Optional#empty()} quando não têm opinião — não é erro uma fonte não se
 * aplicar à requisição atual. Quem recusa é {@link TenantAliasResolver}, e só depois de consultar
 * todas.
 * </p>
 */
public interface TenantAliasSource {

    /** O alias que esta fonte enxerga na requisição atual, se enxergar algum. */
    Optional<String> currentAlias();

    /**
     * Nome da fonte para o log de conflito. O padrão serve; sobrescreva quando a classe tiver nome que
     * não distinga duas instâncias — o operador precisa saber <em>qual</em> das duas está errada.
     */
    default String sourceName() {
        return getClass().getSimpleName();
    }
}
