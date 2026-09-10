package br.com.gems.keycloak.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
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

}
