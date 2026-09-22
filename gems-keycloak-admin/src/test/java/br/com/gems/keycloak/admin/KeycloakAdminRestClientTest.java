package br.com.gems.keycloak.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.gems.exception.exception.ExternalServiceException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakAdminRestClientTest {

    private MockRestServiceServer server;
    private KeycloakAdminRestClient gateway;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl( "http://keycloak" );
        server = MockRestServiceServer.bindTo( builder ).build();
        gateway = new KeycloakAdminRestClient( builder.build(), new KeycloakAdminProperties(
                "http://keycloak", "realm-de-teste", "cliente-de-teste", "segredo-de-teste",
                Duration.ofSeconds( 1 ), Duration.ofSeconds( 1 ) ) );
        server.expect( once(), requestTo( "http://keycloak/realms/realm-de-teste/protocol/openid-connect/token" ) )
                .andExpect( method( POST ) )
                .andRespond( withSuccess( "{\"access_token\":\"token\",\"expires_in\":300}",
                        MediaType.APPLICATION_JSON ) );
    }

    @Test
    void codificaOEmailDaConsultaExatamenteUmaVez() {
        server.expect( once(), request -> assertThat( request.getURI().getRawQuery() )
                        .isEqualTo( "email=ana%2Btag%40example.org&exact=true" ) )
                .andExpect( method( GET ) )
                .andRespond( withSuccess( "[{\"id\":\"u1\",\"username\":\"ana\","
                        + "\"email\":\"ana+tag@example.org\",\"firstName\":\"Ana\","
                        + "\"lastName\":\"Silva\",\"enabled\":true}]", MediaType.APPLICATION_JSON ) );

        assertThat( gateway.findUserByEmail( "ana+tag@example.org" ) ).isPresent();
        server.verify();
    }

    @Test
    void preservaAliasESituacaoAoRenomearOrganizacao() {
        var path = "http://keycloak/admin/realms/realm-de-teste/organizations/o1";
        server.expect( once(), requestTo( path ) ).andExpect( method( GET ) )
                .andRespond( withSuccess( "{\"id\":\"o1\",\"name\":\"Anterior\","
                        + "\"alias\":\"escola_a\",\"enabled\":true}", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( path ) ).andExpect( method( PUT ) )
                .andExpect( content().json( "{\"id\":\"o1\",\"name\":\"Novo Nome\","
                        + "\"alias\":\"escola_a\",\"enabled\":true}" ) )
                .andRespond( withSuccess() );

        gateway.updateOrganization( "o1", "Novo Nome" );
        server.verify();
    }

    @Test
    void invalidaOTokenEmCacheERepeteUmaUnicaVezDepoisDe401() {
        var users = "http://keycloak/admin/realms/realm-de-teste/users?email=ana%40example.org&exact=true";
        server.expect( once(), requestTo( users ) ).andExpect( method( GET ) )
                .andRespond( withStatus( HttpStatus.UNAUTHORIZED ) );
        server.expect( once(), requestTo( "http://keycloak/realms/realm-de-teste/protocol/openid-connect/token" ) )
                .andExpect( method( POST ) )
                .andRespond( withSuccess( "{\"access_token\":\"renovado\",\"expires_in\":300}",
                        MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( users ) ).andExpect( method( GET ) )
                .andRespond( withSuccess( "[]", MediaType.APPLICATION_JSON ) );

        assertThat( gateway.findUserByEmail( "ana@example.org" ) ).isEmpty();
        server.verify();
    }

    /**
     * KA-1. O que importa aqui não é o tipo declarado no {@code throws}, e sim a
     * <strong>hierarquia</strong>: por ser uma {@link ExternalServiceException}, esta falha cai no
     * handler de 502 de {@code gems-exception}. Se a exceção deixasse de herdar dela, a mesma
     * indisponibilidade sairia como 500 sem que nenhuma outra prova percebesse.
     */
    @Test
    void indisponibilidadeDoProvedorViraFalhaDeServicoExterno() {
        server.expect( once(), method( GET ) ).andRespond( withStatus( HttpStatus.SERVICE_UNAVAILABLE ) );

        assertThatThrownBy( () -> gateway.findUserByEmail( "ana@example.org" ) )
                .isInstanceOf( KeycloakAdminException.class )
                .isInstanceOf( ExternalServiceException.class );
    }

    /**
     * KA-2. O serviço que falhou fica na exceção, para o log; a mensagem, que é o que o envelope
     * devolve ao cliente, nomeia a operação e não repassa o corpo do provedor.
     */
    @Test
    void nomeiaOServicoParaOLogESanitizaAMensagemDoCliente() {
        server.expect( once(), method( GET ) )
                .andRespond( withStatus( HttpStatus.INTERNAL_SERVER_ERROR )
                        .body( "{\"error\":\"realm interno indisponivel em db-keycloak-01\"}" )
                        .contentType( MediaType.APPLICATION_JSON ) );

        assertThatThrownBy( () -> gateway.findUserByEmail( "ana@example.org" ) )
                .isInstanceOfSatisfying( KeycloakAdminException.class, falha -> {
                    assertThat( falha.getServico() ).isEqualTo( "keycloak" );
                    assertThat( falha.getMessage() ).contains( "consultar Keycloak" );
                    assertThat( falha.getMessage() ).doesNotContain( "db-keycloak-01" );
                    assertThat( falha.getCause() ).isNotNull();
                } );
    }

    /**
     * O nome do grupo é parâmetro, e não o {@code GESTOR} que a origem no Meduc trazia embutido.
     */
    @Test
    void criaOGrupoComONomePedidoQuandoEleNaoExiste() {
        var path = "http://keycloak/admin/realms/realm-de-teste/organizations/o1/groups";
        server.expect( once(), requestTo( path ) ).andExpect( method( GET ) )
                .andRespond( withSuccess( "[{\"id\":\"g9\",\"name\":\"OUTRO\"}]", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( path ) ).andExpect( method( POST ) )
                .andExpect( content().json( "{\"name\":\"COORDENACAO\"}" ) )
                .andRespond( withSuccess().header( "Location", path + "/g1" ) );

        assertThat( gateway.ensureGroup( "o1", "COORDENACAO" ) ).isEqualTo( "g1" );
        server.verify();
    }

    @Test
    void criaGrupoQuandoARespostaLegadaDeGruposENula() {
        var path = "http://keycloak/admin/realms/realm-de-teste/organizations/o1/groups";
        server.expect( once(), requestTo( path ) ).andExpect( method( GET ) ).andRespond( withSuccess() );
        server.expect( once(), requestTo( path ) ).andExpect( method( POST ) )
                .andRespond( withSuccess().header( "Location", path + "/g1" ) );

        assertThat( gateway.ensureGroup( "o1", "COORDENACAO" ) ).isEqualTo( "g1" );
        server.verify();
    }

    @Test
    void fazSnapshotComAtributosEGruposImutaveis() {
        var user = "http://keycloak/admin/realms/realm-de-teste/users/u1";
        server.expect( once(), requestTo( user ) ).andExpect( method( GET ) )
                .andRespond( withSuccess( "{\"id\":\"u1\",\"username\":\"ana\",\"email\":\"ana@example.org\","
                        + "\"firstName\":\"Ana\",\"lastName\":\"Silva\",\"enabled\":true,"
                        + "\"attributes\":{\"cargo\":[\"DOCENTE\"]}}", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( user + "/groups" ) ).andExpect( method( GET ) )
                .andRespond( withSuccess( "[{\"id\":\"g1\",\"name\":\"Turma\"}]", MediaType.APPLICATION_JSON ) );

        var snapshot = gateway.snapshotUser( "u1" );

        assertThat( snapshot.attributes() ).containsEntry( "cargo", java.util.List.of( "DOCENTE" ) );
        assertThat( snapshot.groupIds() ).containsExactly( "g1" );
        assertThatThrownBy( () -> snapshot.attributes().put( "x", java.util.List.of() ) )
                .isInstanceOf( UnsupportedOperationException.class );
        server.verify();
    }

    @Test
    void compensaACriacaoQuandoResetDeSenhaFalha() {
        var users = "http://keycloak/admin/realms/realm-de-teste/users";
        server.expect( once(), requestTo( users ) ).andExpect( method( POST ) )
                .andRespond( withSuccess().header( "Location", users + "/u1" ) );
        server.expect( once(), requestTo( users + "/u1/reset-password" ) ).andExpect( method( PUT ) )
                .andRespond( withStatus( HttpStatus.SERVICE_UNAVAILABLE ) );
        server.expect( once(), requestTo( users + "/u1" ) ).andExpect( method( DELETE ) )
                .andRespond( withSuccess() );

        assertThatThrownBy( () -> gateway.createUser( "Ana Silva", "ana", "ana@example.org", "senha" ) )
                .isInstanceOf( KeycloakAdminException.class );
        server.verify();
    }

    @Test
    void associaEDesassociaGrupoDeFormaIdempotente() {
        var path = "http://keycloak/admin/realms/realm-de-teste/users/u1/groups/g1";
        server.expect( once(), requestTo( path ) ).andExpect( method( PUT ) )
                .andRespond( withStatus( HttpStatus.CONFLICT ) );
        server.expect( once(), requestTo( path ) ).andExpect( method( DELETE ) )
                .andRespond( withStatus( HttpStatus.NOT_FOUND ) );

        gateway.joinRealmGroup( "u1", "g1" );
        gateway.leaveRealmGroup( "u1", "g1" );
        server.verify();
    }

    @Test
    void preservaFilhosExtrasEAcrescentaSomenteOsDiretosAusentes() {
        var role = "http://keycloak/admin/realms/realm-de-teste/roles/PAI";
        server.expect( once(), requestTo( role ) ).andExpect( method( GET ) )
                .andRespond( withSuccess( "{\"id\":\"r0\",\"name\":\"PAI\"}", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( role + "/composites" ) ).andExpect( method( GET ) )
                .andRespond( withSuccess( "[{\"id\":\"r1\",\"name\":\"FILHO_EXISTENTE\"},"
                        + "{\"id\":\"r9\",\"name\":\"EXTRA\"}]", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( "http://keycloak/admin/realms/realm-de-teste/roles/FILHO_NOVO" ) )
                .andExpect( method( GET ) )
                .andRespond( withSuccess( "{\"id\":\"r2\",\"name\":\"FILHO_NOVO\"}", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( role + "/composites" ) ).andExpect( method( POST ) )
                .andExpect( content().json( "[{\"id\":\"r2\",\"name\":\"FILHO_NOVO\"}]" ) )
                .andRespond( withSuccess() );

        assertThat( gateway.ensureCompositeRealmRole( "PAI", "Papel pai",
                java.util.Set.of( "FILHO_EXISTENTE", "FILHO_NOVO" ) ) ).isEqualTo( 1 );
        server.verify();
    }

    @Test
    void find404EDefineSnapshotAusenteComoFalhaSanitizada() {
        var path = "http://keycloak/admin/realms/realm-de-teste/users/inexistente";
        server.expect( once(), requestTo( path ) ).andRespond( withStatus( HttpStatus.NOT_FOUND ) );
        server.expect( once(), requestTo( path ) ).andRespond( withStatus( HttpStatus.NOT_FOUND ) );

        assertThat( gateway.findUserById( "inexistente" ) ).isEmpty();
        assertThatThrownBy( () -> gateway.snapshotUser( "inexistente" ) )
                .isInstanceOf( KeycloakAdminException.class )
                .hasMessageContaining( "obter retrato de conta" );
        server.verify();
    }

    @Test
    void respostaNulaOuMalformadaDeUsuarioOuGruposViraFalhaSanitizada() {
        var user = "http://keycloak/admin/realms/realm-de-teste/users/u1";
        server.expect( once(), requestTo( user ) ).andRespond( withSuccess() );
        server.expect( once(), requestTo( user + "/groups" ) )
                .andRespond( withSuccess( "[{\"id\":null,\"name\":\"Grupo\"}]", MediaType.APPLICATION_JSON ) );

        assertThatThrownBy( () -> gateway.findUserById( "u1" ) )
                .isInstanceOf( KeycloakAdminException.class );
        assertThatThrownBy( () -> gateway.listUserGroupIds( "u1" ) )
                .isInstanceOf( KeycloakAdminException.class );
        server.verify();
    }

    @Test
    void atualizaHabilitaDesabilitaEExcluiUsuario() {
        var user = "http://keycloak/admin/realms/realm-de-teste/users/u1";
        var snapshot = new KeycloakUserSnapshot( "u1", "Ana", "Silva", "ana", "ana@example.org", true,
                java.util.Map.of( "cargo", java.util.List.of( "DOCENTE" ) ), java.util.Set.of() );
        server.expect( once(), requestTo( user ) ).andExpect( method( PUT ) ).andExpect( content().json(
                "{\"id\":\"u1\",\"firstName\":\"Ana\",\"lastName\":\"Silva\",\"username\":\"ana\",\"email\":\"ana@example.org\",\"enabled\":true,\"attributes\":{\"cargo\":[\"DOCENTE\"]}}" ) ).andRespond( withSuccess() );
        server.expect( once(), requestTo( user ) ).andExpect( method( GET ) )
                .andRespond( withSuccess( userJson( true ), MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( user + "/groups" ) ).andRespond( withSuccess( "[]", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( user ) ).andExpect( method( PUT ) )
                .andExpect( content().json( "{\"enabled\":false}" ) ).andRespond( withSuccess() );
        server.expect( once(), requestTo( user ) ).andExpect( method( DELETE ) ).andRespond( withSuccess() );

        gateway.updateUser( snapshot );
        gateway.setUserEnabled( "u1", false );
        gateway.deleteUser( "u1" );
        server.verify();
    }

    @Test
    void restauraRemovendoGruposExtrasEAcrescentandoAusentes() {
        var user = "http://keycloak/admin/realms/realm-de-teste/users/u1";
        var snapshot = new KeycloakUserSnapshot( "u1", "Ana", "Silva", "ana", "ana@example.org", true,
                java.util.Map.of(), java.util.Set.of( "novo" ) );
        server.expect( once(), requestTo( user ) ).andExpect( method( PUT ) ).andRespond( withSuccess() );
        server.expect( once(), requestTo( user + "/groups" ) ).andRespond( withSuccess(
                "[{\"id\":\"extra\",\"name\":\"Extra\"}]", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( user + "/groups/extra" ) ).andExpect( method( DELETE ) )
                .andRespond( withSuccess() );
        server.expect( once(), requestTo( user + "/groups/novo" ) ).andExpect( method( PUT ) )
                .andRespond( withSuccess() );

        gateway.restoreUser( snapshot );
        server.verify();
    }

    @Test
    void falhaDaCompensacaoFicaSuprimidaNaFalhaPrincipal() {
        var users = "http://keycloak/admin/realms/realm-de-teste/users";
        server.expect( once(), requestTo( users ) ).andRespond( withSuccess().header( "Location", users + "/u1" ) );
        server.expect( once(), requestTo( users + "/u1/reset-password" ) ).andRespond( withStatus( HttpStatus.SERVICE_UNAVAILABLE ) );
        server.expect( once(), requestTo( users + "/u1" ) ).andRespond( withStatus( HttpStatus.INTERNAL_SERVER_ERROR ) );

        assertThatThrownBy( () -> gateway.createUser( "Ana Silva", "ana", "ana@example.org", "senha" ) )
                .isInstanceOfSatisfying( KeycloakAdminException.class,
                        failure -> assertThat( failure.getSuppressed() ).hasSize( 1 ) );
        server.verify();
    }

    @Test
    void criaRoleSomenteQuandoAusenteETrataConflitoComoIdempotente() {
        var roles = "http://keycloak/admin/realms/realm-de-teste/roles";
        server.expect( once(), requestTo( roles + "/EXISTENTE" ) ).andRespond( withSuccess( "{}", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( roles + "/NOVA" ) ).andRespond( withStatus( HttpStatus.NOT_FOUND ) );
        server.expect( once(), requestTo( roles ) ).andExpect( method( POST ) ).andRespond( withSuccess() );
        server.expect( once(), requestTo( roles + "/CORRENTE" ) ).andRespond( withStatus( HttpStatus.NOT_FOUND ) );
        server.expect( once(), requestTo( roles ) ).andExpect( method( POST ) ).andRespond( withStatus( HttpStatus.CONFLICT ) );

        assertThat( gateway.ensureRealmRole( "EXISTENTE", "x" ) ).isFalse();
        assertThat( gateway.ensureRealmRole( "NOVA", "x" ) ).isTrue();
        assertThat( gateway.ensureRealmRole( "CORRENTE", "x" ) ).isFalse();
        server.verify();
    }

    @Test
    void erroDeRoleQueNaoEConflitoPermaneceFalhaSanitizada() {
        var roles = "http://keycloak/admin/realms/realm-de-teste/roles";
        server.expect( once(), requestTo( roles + "/FALHA" ) ).andRespond( withStatus( HttpStatus.NOT_FOUND ) );
        server.expect( once(), requestTo( roles ) ).andRespond( withStatus( HttpStatus.SERVICE_UNAVAILABLE )
                .body( "segredo do provedor" ) );

        assertThatThrownBy( () -> gateway.ensureRealmRole( "FALHA", "x" ) )
                .isInstanceOfSatisfying( KeycloakAdminException.class,
                        failure -> assertThat( failure.getMessage() ).doesNotContain( "segredo do provedor" ) );
        server.verify();
    }

    @Test
    void composicaoSemFilhosFaltantesNaoFazPost() {
        var role = "http://keycloak/admin/realms/realm-de-teste/roles/PAI";
        server.expect( once(), requestTo( role ) ).andRespond( withSuccess( "{}", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( role + "/composites" ) ).andRespond(
                withSuccess( "[{\"name\":\"FILHO\"}]", MediaType.APPLICATION_JSON ) );

        assertThat( gateway.ensureCompositeRealmRole( "PAI", "x", java.util.Set.of( "FILHO" ) ) ).isZero();
        server.verify();
    }

    @Test
    void snapshotDeRespostaMalformadaEListaNulaSaoSanitizados() {
        var user = "http://keycloak/admin/realms/realm-de-teste/users/u1";
        server.expect( once(), requestTo( user ) ).andRespond( withSuccess(
                "{\"id\":null,\"username\":\"ana\",\"email\":\"ana@example.org\",\"firstName\":\"Ana\",\"lastName\":\"Silva\",\"enabled\":true}",
                MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( user + "/groups" ) ).andRespond( withSuccess( "[]", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( user + "/groups" ) ).andRespond( withSuccess() );

        assertThatThrownBy( () -> gateway.snapshotUser( "u1" ) ).isInstanceOf( KeycloakAdminException.class );
        assertThatThrownBy( () -> gateway.listUserGroupIds( "u1" ) ).isInstanceOf( KeycloakAdminException.class );
        server.verify();
    }

    @Test
    void conflitoAoComporFilhosMantemResultadoAditivoIdempotente() {
        var role = "http://keycloak/admin/realms/realm-de-teste/roles/PAI";
        server.expect( once(), requestTo( role ) ).andRespond( withSuccess( "{}", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( role + "/composites" ) ).andRespond( withSuccess( "[]", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( "http://keycloak/admin/realms/realm-de-teste/roles/FILHO" ) )
                .andRespond( withSuccess( "{\"name\":\"FILHO\"}", MediaType.APPLICATION_JSON ) );
        server.expect( once(), requestTo( role + "/composites" ) ).andExpect( method( POST ) )
                .andRespond( withStatus( HttpStatus.CONFLICT ) );

        assertThat( gateway.ensureCompositeRealmRole( "PAI", "x", java.util.Set.of( "FILHO" ) ) ).isZero();
        server.verify();
    }

    private static String userJson( boolean enabled ) {
        return "{\"id\":\"u1\",\"username\":\"ana\",\"email\":\"ana@example.org\","
                + "\"firstName\":\"Ana\",\"lastName\":\"Silva\",\"enabled\":" + enabled + ",\"attributes\":{}}";
    }

}
