package br.com.gems.keycloak.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KeycloakPublicSurfaceTest {

    @Test
    void expoeOsTresTiposPublicosDoContratoDeLifecycleERoles() throws Exception {
        var snapshot = Class.forName( "br.com.gems.keycloak.admin.KeycloakUserSnapshot" );
        var lifecycle = Class.forName( "br.com.gems.keycloak.admin.KeycloakUserLifecycleGateway" );
        var roles = Class.forName( "br.com.gems.keycloak.admin.KeycloakRealmRoleGateway" );

        assertThat( Modifier.isPublic( snapshot.getModifiers() ) ).isTrue();
        assertThat( snapshot.isRecord() ).isTrue();
        assertThat( snapshot.getDeclaredConstructor( String.class, String.class, String.class, String.class,
                String.class, boolean.class, Map.class, Set.class ) ).isNotNull();
        assertThat( Modifier.isPublic( lifecycle.getModifiers() ) ).isTrue();
        assertThat( Modifier.isPublic( roles.getModifiers() ) ).isTrue();
        assertThat( lifecycle.getMethod( "findUserById", String.class ).getReturnType() )
                .isEqualTo( Optional.class );
        assertThat( roles.getMethod( "ensureRealmRole", String.class, String.class ).getReturnType() )
                .isEqualTo( boolean.class );
        assertThat( roles.getMethod( "ensureCompositeRealmRole", String.class, String.class, Set.class )
                .getReturnType() ).isEqualTo( int.class );
    }

    @Test
    void snapshotCopiaProfundamenteAsListasDeAtributos() {
        var values = new java.util.ArrayList<>( List.of( "DOCENTE" ) );
        var attributes = new java.util.HashMap<String, List<String>>();
        attributes.put( "cargo", values );

        var snapshot = new KeycloakUserSnapshot( "u1", "Ana", "Silva", "ana", "ana@example.org", true,
                attributes, Set.of( "g1" ) );
        values.add( "GESTOR" );
        attributes.put( "outro", List.of( "valor" ) );

        assertThat( snapshot.attributes() ).containsOnly( org.assertj.core.api.Assertions.entry( "cargo", List.of( "DOCENTE" ) ) );
        assertThatThrownBy( () -> snapshot.attributes().get( "cargo" ).add( "GESTOR" ) )
                .isInstanceOf( UnsupportedOperationException.class );
    }

}
