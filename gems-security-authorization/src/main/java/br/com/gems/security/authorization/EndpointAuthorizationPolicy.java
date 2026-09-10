package br.com.gems.security.authorization;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

/**
 * A regra: um endpoint é público por declaração, ou exige uma ação concreta do catálogo do seu
 * escopo.
 * <p>
 * <strong>Perfil genérico é recusado, e é essa a razão de a classe existir</strong> (SA-3).
 * {@code hasRole('GESTOR')} parece proteção e não é: ele autoriza tudo o que um gestor um dia venha a
 * poder fazer, inclusive o que ainda não foi escrito. A exigência de {@code AREA_VERBO} força a
 * decisão a ser tomada por endpoint, na hora em que o endpoint é escrito.
 * </p>
 * <p>
 * A classe devolve as violações em vez de lançar. Quem varre precisa da <em>lista</em> — parar no
 * primeiro endpoint desprotegido faria a correção acontecer um build por vez.
 * </p>
 */
public final class EndpointAuthorizationPolicy {

    static final String SEM_CLASSIFICACAO = "endpoint sem classificação";
    static final String CLASSIFICACAO_AMBIGUA = "classificações mutuamente exclusivas";
    static final String SEM_PRE_AUTHORIZE = "endpoint de negócio sem @PreAuthorize";
    static final String SEM_ACAO_CONCRETA = "@PreAuthorize deve exigir uma ação concreta com hasRole";
    static final String FORA_DO_CATALOGO = "ação fora do catálogo do escopo";

    private static final Pattern HAS_ROLE =
            Pattern.compile( "^hasRole\\('([A-Z][A-Z0-9]*_[A-Z0-9_]+)'\\)$" );

    private EndpointAuthorizationPolicy() {
    }

    /** As violações do método, ou lista vazia se ele estiver conforme. */
    public static List<String> violations( HandlerMethod method, AuthorizationCatalog catalog ) {
        var violations = new ArrayList<String>();
        boolean publicEndpoint = has( method, PublicEndpoint.class );
        boolean globalEndpoint = has( method, GlobalEndpoint.class );
        boolean tenantEndpoint = has( method, TenantEndpoint.class );
        int count = ( publicEndpoint ? 1 : 0 ) + ( globalEndpoint ? 1 : 0 ) + ( tenantEndpoint ? 1 : 0 );
        if ( count != 1 ) {
            violations.add( count == 0 ? SEM_CLASSIFICACAO : CLASSIFICACAO_AMBIGUA );
            return violations;
        }
        if ( publicEndpoint ) return violations;

        var preAuthorize = find( method, PreAuthorize.class );
        if ( preAuthorize == null ) {
            violations.add( SEM_PRE_AUTHORIZE );
            return violations;
        }
        var matcher = HAS_ROLE.matcher( preAuthorize.value() );
        if ( !matcher.matches() ) {
            violations.add( SEM_ACAO_CONCRETA );
            return violations;
        }
        var action = matcher.group( 1 );
        var scope = tenantEndpoint ? AuthorizationScope.TENANT : AuthorizationScope.GLOBAL;
        if ( !catalog.actionsOf( scope ).contains( action ) ) violations.add( FORA_DO_CATALOGO );
        return violations;
    }

    private static <A extends Annotation> boolean has( HandlerMethod method, Class<A> type ) {
        return find( method, type ) != null;
    }

    private static <A extends Annotation> A find( HandlerMethod method, Class<A> type ) {
        var annotation = AnnotatedElementUtils.findMergedAnnotation( method.getMethod(), type );
        return annotation != null
                ? annotation
                : AnnotatedElementUtils.findMergedAnnotation( method.getBeanType(), type );
    }

}
