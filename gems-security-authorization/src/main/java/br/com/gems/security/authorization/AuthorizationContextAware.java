package br.com.gems.security.authorization;

/**
 * O que a autenticação do consumidor implementa para expor o contexto de autorização à SDK.
 * <p>
 * A origem no Meduc amarrava o interceptor a uma classe concreta de token. Uma biblioteca não pode
 * exigir que todo consumidor tenha <em>aquela</em> classe — ela precisa de um contrato mínimo, e este
 * é ele: implemente na sua {@code Authentication} e o interceptor passa a valer sem mais nada.
 * </p>
 */
public interface AuthorizationContextAware {

    JwtAuthorizationContext authorizationContext();

}
