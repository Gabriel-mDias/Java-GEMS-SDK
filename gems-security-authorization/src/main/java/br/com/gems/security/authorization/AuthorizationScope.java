package br.com.gems.security.authorization;

/**
 * O alcance de uma ação: dado que pertence a uma organização, ou dado que não pertence a nenhuma.
 * <p>
 * Os dois conjuntos são <strong>disjuntos por construção</strong> ({@link AuthorizationCatalog}), e a
 * separação não é organizacional: uma ação global exercida sobre dado de organização, ou o contrário,
 * é exatamente o defeito que vaza dado entre clientes.
 * </p>
 */
public enum AuthorizationScope {

    /** Vale para dado que não pertence a organização alguma — identidade, cadastro, referência. */
    GLOBAL,

    /** Vale dentro de uma organização, e exige contexto de organização comprovado. */
    TENANT

}
