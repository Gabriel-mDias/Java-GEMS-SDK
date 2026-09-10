package br.com.gems.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizationCatalogTest {

    @Test
    void derivaOsDoisEscoposDoEnumDeAcoes() {
        var catalogo = AuthorizationCatalog.of( AcaoDeTeste.class );

        assertThat( catalogo.globalActions() )
                .containsExactlyInAnyOrder( "CONSULTAR_ORGANIZACAO", "ALTERAR_ORGANIZACAO" );
        assertThat( catalogo.tenantActions() ).containsExactly( "CONSULTAR_TURMA" );
    }

    @Test
    void escopoDaAcaoDecideOConjuntoEmQueElaCai() {
        var catalogo = AuthorizationCatalog.of( AcaoDeTeste.class );

        assertThat( catalogo.actionsOf( AuthorizationScope.GLOBAL ) ).contains( "ALTERAR_ORGANIZACAO" );
        assertThat( catalogo.actionsOf( AuthorizationScope.TENANT ) )
                .doesNotContain( "ALTERAR_ORGANIZACAO" );
    }

    @Test
    void recusaAMesmaAcaoNosDoisEscopos() {
        assertThatThrownBy( () -> new AuthorizationCatalog( Set.of( "TURMA_LER" ), Set.of( "TURMA_LER" ) ) )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "disjuntos" );
    }

    @Test
    void recusaPerfilGenericoComoSeFosseAcao() {
        assertThatThrownBy( () -> new AuthorizationCatalog( Set.of( "GESTOR" ), Set.of() ) )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "GESTOR" );
    }

    /** Catálogo vazio reprovaria todo endpoint de negócio, e a causa seria difícil de enxergar. */
    @Test
    void recusaEnumSemAcaoAlguma() {
        assertThatThrownBy( () -> AuthorizationCatalog.of( SemAcao.class ) )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "não declara ação alguma" );
    }

    enum SemAcao implements AuthorizationAction {
        ;

        @Override
        public AuthorizationScope scope() {
            return AuthorizationScope.GLOBAL;
        }
    }

}
