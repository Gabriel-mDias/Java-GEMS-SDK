package br.com.gems.firebase.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Collections;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Filtro stateless; consumidor o instala uma única vez na própria SecurityFilterChain. */
public final class FirebaseAuthenticationFilter extends OncePerRequestFilter {
    private final IdTokenVerifier verifier;
    private final AuthorizationResolver resolver;
    private final Clock clock;
    private final String tenantHeader;
    private final SecurityEventSink events;
    public FirebaseAuthenticationFilter(IdTokenVerifier verifier, AuthorizationResolver resolver,
            Clock clock, String tenantHeader) {
        this(verifier, resolver, clock, tenantHeader, new LoggingSecurityEventPublisher());
    }
    public FirebaseAuthenticationFilter(IdTokenVerifier verifier, AuthorizationResolver resolver,
            Clock clock, String tenantHeader, SecurityEventSink events) {
        this.verifier = verifier; this.resolver = resolver; this.clock = clock; this.tenantHeader = tenantHeader;
        this.events = events;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        java.util.UUID correlation = java.util.UUID.randomUUID();
        response.setHeader("X-Correlation-ID", correlation.toString());
        try {
            try {
                var headers = Collections.list(request.getHeaders("Authorization"));
                if (!headers.isEmpty()) {
                    if (headers.size() != 1 || !headers.getFirst().startsWith("Bearer ")
                            || headers.getFirst().length() <= 7) {
                        throw new FirebaseAuthException(FirebaseAuthException.Reason.INVALID_TOKEN);
                    }
                    var aliases = Collections.list(request.getHeaders(tenantHeader));
                    if (aliases.size() > 1) { throw FirebaseAuthException.denied(); }
                    var identity = verifier.verify(headers.getFirst().substring(7));
                    var resolution = resolver.resolve(identity, aliases.isEmpty() ? Optional.empty()
                            : Optional.of(aliases.getFirst()), clock.instant());
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new FirebaseAuthenticationToken(resolution));
                    SecurityContextHolder.setContext(context);
                }
            } catch (RuntimeException failure) {
                FirebaseAuthException.Reason reason = failure instanceof FirebaseAuthException known ? known.reason()
                        : FirebaseAuthException.Reason.PROVIDER_UNAVAILABLE;
                try {
                    events.emit(new SecurityEventSink.Event(java.util.UUID.randomUUID(), "ACCESS_REFUSED_" + reason.name(),
                            null, null, clock.instant(), null, SecurityEventSink.TargetType.AUTHENTICATION,
                            null, null, null, null, correlation));
                } catch (RuntimeException unavailable) { /* auditoria não abre acesso */ }
                response.setStatus(reason.status());
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"" + reason.name() + "\"}");
                return;
            }
            chain.doFilter(request, response);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
