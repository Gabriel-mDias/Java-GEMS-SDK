package br.com.gems.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

/**
 * SA-1, SA-2 e SA-3 sobre a varredura — não sobre a política isolada.
 * <p>
 * A diferença importa: o consumidor não chama {@code EndpointAuthorizationPolicy}, ele varre. Provar
 * só a política deixaria de fora justamente o que a varredura acrescenta — percorrer tudo, nomear o
 * endpoint e não parar no primeiro achado.
 * </p>
 */
class EndpointAuthorizationScanTest {

    private final AuthorizationCatalog catalogo = AuthorizationCatalog.of( AcaoDeTeste.class );

    /** SA-1. */
    @Test
    void aceitaCadaEscopoComAcaoDoCatalogoCorreto() {
        assertThat( varrer( "publico", "global", "tenant" ) ).isEmpty();
    }

    /** SA-2 — e o endpoint aparece nomeado, senão a varredura diria apenas que "algo" está errado. */
    @Test
    void reprovaEndpointSemProtecaoNomeandoOEndpoint() {
        var violacoes = varrer( "semClassificacao" );

        assertThat( violacoes ).singleElement().satisfies( violacao -> {
            assertThat( violacao.endpoint() ).isEqualTo( "ControladorSintetico#semClassificacao" );
            assertThat( violacao.reason() ).isEqualTo( EndpointAuthorizationPolicy.SEM_CLASSIFICACAO );
        } );
    }

    /** SA-2, o outro caminho: classificado, mas sem exigência alguma. */
    @Test
    void reprovaEndpointDeNegocioSemPreAuthorize() {
        assertThat( varrer( "tenantSemAcao" ) ).extracting( EndpointAuthorizationScan.Violation::reason )
                .containsExactly( EndpointAuthorizationPolicy.SEM_PRE_AUTHORIZE );
    }

    /** SA-3. */
    @Test
    void reprovaEndpointProtegidoPorPerfilGenerico() {
        assertThat( varrer( "tenantComPerfil" ) ).extracting( EndpointAuthorizationScan.Violation::reason )
                .containsExactly( EndpointAuthorizationPolicy.SEM_ACAO_CONCRETA );
    }

    @Test
    void reprovaAcaoDoEscopoErradoEClassificacaoAmbigua() {
        assertThat( varrer( "tenantComAcaoGlobal" ) )
                .extracting( EndpointAuthorizationScan.Violation::reason )
                .containsExactly( EndpointAuthorizationPolicy.FORA_DO_CATALOGO );
        assertThat( varrer( "duplicado" ) ).extracting( EndpointAuthorizationScan.Violation::reason )
                .containsExactly( EndpointAuthorizationPolicy.CLASSIFICACAO_AMBIGUA );
    }

    /** A varredura não para no primeiro achado: corrigir um endpoint por build é o que ela evita. */
    @Test
    void relataTodosOsEndpointsForaDaRegraDeUmaVez() {
        var violacoes = varrer( "semClassificacao", "tenantSemAcao", "tenantComPerfil", "global" );

        assertThat( violacoes ).hasSize( 3 )
                .extracting( EndpointAuthorizationScan.Violation::endpoint )
                .doesNotContain( "ControladorSintetico#global" );
    }

    @Test
    void aRecusaNomeiaTodosOsEndpointsInfratores() {
        assertThatThrownBy( () -> EndpointAuthorizationScan.assertProtected(
                handlers( "semClassificacao", "tenantComPerfil" ), catalogo ) )
                .isInstanceOf( UnprotectedEndpointException.class )
                .hasMessageContaining( "semClassificacao" )
                .hasMessageContaining( "tenantComPerfil" );
    }

    @Test
    void aceitaSemRecusarQuandoTodosEstaoConformes() {
        EndpointAuthorizationScan.assertProtected( handlers( "publico", "global", "tenant" ), catalogo );
    }

    private List<EndpointAuthorizationScan.Violation> varrer( String... metodos ) {
        return EndpointAuthorizationScan.scan( handlers( metodos ), catalogo );
    }

    private static List<HandlerMethod> handlers( String... metodos ) {
        var controlador = new ControladorSintetico();
        return Arrays.stream( metodos ).map( nome -> new HandlerMethod( controlador, metodo( nome ) ) )
                .toList();
    }

    private static Method metodo( String nome ) {
        try {
            return ControladorSintetico.class.getDeclaredMethod( nome );
        } catch ( NoSuchMethodException excecao ) {
            throw new IllegalArgumentException( "Método sintético inexistente: " + nome, excecao );
        }
    }

    /**
     * Os endpoints de teste. É aqui que a mutação de T075 e T076 acontece — remover a proteção de
     * {@code global}, ou trocar a ação por um perfil.
     */
    static class ControladorSintetico {
        public void semClassificacao() {}

        @PublicEndpoint
        public void publico() {}

        @PublicEndpoint
        @TenantEndpoint
        public void duplicado() {}

        @TenantEndpoint
        public void tenantSemAcao() {}

        @TenantEndpoint
        @PreAuthorize( "hasRole('GESTOR')" )
        public void tenantComPerfil() {}

        @GlobalEndpoint
        @PreAuthorize( "hasRole('CONSULTAR_ORGANIZACAO')" )
        public void global() {}

        @TenantEndpoint
        @PreAuthorize( "hasRole('CONSULTAR_TURMA')" )
        public void tenant() {}

        @TenantEndpoint
        @PreAuthorize( "hasRole('CONSULTAR_ORGANIZACAO')" )
        public void tenantComAcaoGlobal() {}
    }

}
