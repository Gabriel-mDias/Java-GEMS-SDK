package br.com.gems.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

/**
 * O interceptor falha fechado: sem organização comprovada, endpoint de organização não roda.
 * <p>
 * As três formas de "sem organização" são provadas separadamente porque elas quebram por caminhos
 * diferentes — e uma implementação pode fechar uma e deixar as outras abertas.
 * </p>
 */
class TenantAuthorizationInterceptorTest {

    private final TenantAuthorizationInterceptor interceptor = new TenantAuthorizationInterceptor();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recusaEndpointDeOrganizacaoSemAutenticacaoAlguma() {
        assertThatThrownBy( () -> interceptor.preHandle( null, null, handler( "tenant" ) ) )
                .isInstanceOf( AccessDeniedException.class );
    }

    @Test
    void recusaAutenticacaoQueNaoExpoeContextoDeAutorizacao() {
        SecurityContextHolder.getContext().setAuthentication( new TokenSemContexto() );

        assertThatThrownBy( () -> interceptor.preHandle( null, null, handler( "tenant" ) ) )
                .isInstanceOf( AccessDeniedException.class );
    }

    @Test
    void recusaContextoDeAutorizacaoSemOrganizacao() {
        autenticarCom( Optional.empty() );

        assertThatThrownBy( () -> interceptor.preHandle( null, null, handler( "tenant" ) ) )
                .isInstanceOf( AccessDeniedException.class );
    }

    @Test
    void permiteQuandoAOrganizacaoEstaComprovada() {
        autenticarCom( Optional.of( "escola_a" ) );

        assertThat( interceptor.preHandle( null, null, handler( "tenant" ) ) ).isTrue();
    }

    /** Endpoint que não é de organização não é assunto deste interceptor, autenticado ou não. */
    @Test
    void ignoraEndpointQueNaoEDeOrganizacao() {
        assertThat( interceptor.preHandle( null, null, handler( "global" ) ) ).isTrue();
        assertThat( interceptor.preHandle( null, null, new Object() ) ).isTrue();
    }

    private static void autenticarCom( Optional<String> alias ) {
        var contexto = new JwtAuthorizationContext( Set.of(), Set.of(), Set.of(),
                alias.isPresent() ? Set.of( "CONSULTAR_TURMA" ) : Set.of(), alias );
        SecurityContextHolder.getContext().setAuthentication( new TokenComContexto( contexto ) );
    }

    private static HandlerMethod handler( String nome ) {
        try {
            Method metodo = Controlador.class.getDeclaredMethod( nome );
            return new HandlerMethod( new Controlador(), metodo );
        } catch ( NoSuchMethodException excecao ) {
            throw new IllegalArgumentException( nome, excecao );
        }
    }

    static class Controlador {
        @TenantEndpoint
        public void tenant() {}

        @GlobalEndpoint
        public void global() {}
    }

    static class TokenSemContexto extends AbstractAuthenticationToken {
        TokenSemContexto() {
            super( Set.of() );
        }

        @Override
        public Object getCredentials() {
            return "";
        }

        @Override
        public Object getPrincipal() {
            return "sem-contexto";
        }
    }

    static class TokenComContexto extends AbstractAuthenticationToken implements AuthorizationContextAware {
        private final JwtAuthorizationContext contexto;

        TokenComContexto( JwtAuthorizationContext contexto ) {
            super( Set.of() );
            this.contexto = contexto;
        }

        @Override
        public JwtAuthorizationContext authorizationContext() {
            return contexto;
        }

        @Override
        public Object getCredentials() {
            return "";
        }

        @Override
        public Object getPrincipal() {
            return "com-contexto";
        }
    }

}
