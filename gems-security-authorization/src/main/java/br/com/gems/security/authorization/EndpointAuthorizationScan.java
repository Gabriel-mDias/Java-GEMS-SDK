package br.com.gems.security.authorization;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * A varredura: aplica {@link EndpointAuthorizationPolicy} a todos os endpoints publicados e nomeia os
 * que estão fora da regra.
 * <p>
 * <strong>É reaproveitável de propósito.</strong> A origem no Meduc era um teste — e um teste protege
 * um projeto. Como mecanismo da SDK, a mesma varredura vale para qualquer consumidor, que a liga ao
 * seu build com uma prova de três linhas em vez de reescrevê-la.
 * </p>
 * <p>
 * Ela varre o {@link RequestMappingHandlerMapping}, isto é, o que o Spring <em>de fato</em> publicou —
 * e não uma lista de controladores que alguém manteve à mão. Um endpoint novo entra na varredura por
 * existir; é o que impede que a cobertura caduque sem ninguém notar.
 * </p>
 */
public final class EndpointAuthorizationScan {

    private EndpointAuthorizationScan() {
    }

    /** Um endpoint fora da regra, e o motivo. */
    public record Violation(String endpoint, String reason) {
        @Override
        public String toString() {
            return endpoint + ": " + reason;
        }
    }

    /** Varre o que a aplicação publicou. */
    public static List<Violation> scan( RequestMappingHandlerMapping mapping, AuthorizationCatalog catalog ) {
        Objects.requireNonNull( mapping, "mapping" );
        return scan( mapping.getHandlerMethods().values(), catalog );
    }

    /** Varre um conjunto de métodos de tratamento — a forma que o teste usa. */
    public static List<Violation> scan( Collection<HandlerMethod> methods, AuthorizationCatalog catalog ) {
        Objects.requireNonNull( methods, "methods" );
        Objects.requireNonNull( catalog, "catalog" );
        return methods.stream()
                .flatMap( method -> EndpointAuthorizationPolicy.violations( method, catalog ).stream()
                        .map( reason -> new Violation( nomeDe( method ), reason ) ) )
                .sorted( Comparator.comparing( Violation::endpoint ).thenComparing( Violation::reason ) )
                .toList();
    }

    /**
     * Recusa se houver qualquer violação, nomeando todas.
     *
     * @throws UnprotectedEndpointException com a lista completa — corrigir um endpoint por build seria
     *         o que uma varredura existe para evitar.
     */
    public static void assertProtected( RequestMappingHandlerMapping mapping, AuthorizationCatalog catalog ) {
        recusar( scan( mapping, catalog ) );
    }

    /** Idem, sobre um conjunto de métodos de tratamento. */
    public static void assertProtected( Collection<HandlerMethod> methods, AuthorizationCatalog catalog ) {
        recusar( scan( methods, catalog ) );
    }

    private static void recusar( List<Violation> violacoes ) {
        if ( violacoes.isEmpty() ) return;
        throw new UnprotectedEndpointException( "Endpoints fora da política de autorização:\n"
                + violacoes.stream().map( Violation::toString ).collect( Collectors.joining( "\n" ) ) );
    }

    private static String nomeDe( HandlerMethod method ) {
        return method.getBeanType().getSimpleName() + "#" + method.getMethod().getName();
    }

}
