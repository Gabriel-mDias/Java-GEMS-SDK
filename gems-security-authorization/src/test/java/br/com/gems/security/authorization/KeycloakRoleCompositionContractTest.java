package br.com.gems.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

/**
 * A composição de roles no provedor recebe ações concretas do catálogo; perfil genérico não é uma
 * segunda fonte de autorização dentro deste módulo.
 */
class KeycloakRoleCompositionContractTest {

    @Test
    void catalogoEntregaAcoesConcretasEDisjuntasParaComposicaoDeRoles() {
        var catalogo = AuthorizationCatalog.of( AcaoDeTeste.class );
        var todas = new HashSet<>( catalogo.globalActions() );

        todas.addAll( catalogo.tenantActions() );

        assertThat( todas ).allSatisfy( action ->
                assertThat( AuthorizationCatalog.ACTION.matcher( action ).matches() ).isTrue() );
        assertThat( catalogo.globalActions() ).doesNotContainAnyElementsOf( catalogo.tenantActions() );
    }

}
