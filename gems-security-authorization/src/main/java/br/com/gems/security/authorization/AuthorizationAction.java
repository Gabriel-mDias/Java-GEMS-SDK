package br.com.gems.security.authorization;

/**
 * Uma ação concreta do sistema — a unidade de autorização.
 * <p>
 * <strong>O consumidor a implementa com um enum, e é isso que faz a fonte ser única.</strong> Um enum
 * declara nome e escopo num lugar só, e o compilador passa a provar o que antes era conferido por
 * teste de paridade entre arquivos escritos à mão. {@code name()} vem de graça de
 * {@link java.lang.Enum} — a interface não precisa que ninguém o escreva.
 * </p>
 *
 * <pre>{@code
 * public enum AcaoDoMeduc implements AuthorizationAction {
 *     CONSULTAR_ORGANIZACAO( GLOBAL ),
 *     CONSULTAR_CONTEXTO_ORGANIZACAO( TENANT );
 *
 *     private final AuthorizationScope escopo;
 *     AcaoDoMeduc( AuthorizationScope escopo ) { this.escopo = escopo; }
 *
 *     @Override public AuthorizationScope scope() { return escopo; }
 * }
 * }</pre>
 *
 * @see AuthorizationCatalog#of(Class) o catálogo derivado do enum
 * @see FrontendActionCatalogGenerator a representação de frontend derivada do mesmo enum
 */
public interface AuthorizationAction {

    /** O nome da ação, no formato {@code AREA_VERBO}. Um enum já o fornece. */
    String name();

    /** Onde a ação vale. */
    AuthorizationScope scope();

}
