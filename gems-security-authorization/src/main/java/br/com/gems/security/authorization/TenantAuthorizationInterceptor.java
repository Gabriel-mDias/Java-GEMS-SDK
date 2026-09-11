package br.com.gems.security.authorization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Recusa endpoint {@link TenantEndpoint} quando a requisição não comprova organização.
 * <p>
 * <strong>Falha fechado.</strong> Autenticação que não implementa {@link AuthorizationContextAware},
 * ausência de autenticação, ou contexto sem alias — os três recusam. Tratar o desconhecido como
 * permitido é o defeito que a rodada fechou em outros três lugares da SDK, e aqui ele custaria o
 * mesmo: um endpoint de organização atendido sem organização opera sobre o que estiver instalado na
 * thread.
 * </p>
 * <p>
 * A recusa é {@link AccessDeniedException}, que {@code gems-exception} devolve como <b>403</b>
 * (SA-4). Não é 401: quem chegou aqui está autenticado; o que falta é escopo, e mandá-lo ao login não
 * resolveria nada.
 * </p>
 */
public final class TenantAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle( HttpServletRequest request, HttpServletResponse response, Object handler ) {
        if ( !( handler instanceof HandlerMethod method ) || !isTenant( method ) ) return true;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if ( !( authentication instanceof AuthorizationContextAware aware )
                || !aware.authorizationContext().hasTenant() ) {
            throw new AccessDeniedException( "Contexto de organização obrigatório" );
        }
        return true;
    }

    private static boolean isTenant( HandlerMethod method ) {
        return AnnotatedElementUtils.hasAnnotation( method.getMethod(), TenantEndpoint.class )
                || AnnotatedElementUtils.hasAnnotation( method.getBeanType(), TenantEndpoint.class );
    }

}
