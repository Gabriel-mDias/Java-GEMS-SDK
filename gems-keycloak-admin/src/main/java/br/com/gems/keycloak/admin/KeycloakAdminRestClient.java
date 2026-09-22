package br.com.gems.keycloak.admin;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * O transporte: a API de administração do Keycloak por trás de {@link KeycloakAdminGateway}.
 * <p>
 * <strong>Toda chamada passa por {@link #authorized(String, Function)}</strong>, e é ali que KA-1 se
 * cumpre: qualquer {@link RuntimeException} que suba do RestClient vira
 * {@link KeycloakAdminException}, nomeando a operação e preservando a causa. Deixar uma exceção do
 * cliente HTTP escapar faria indisponibilidade de terceiro chegar ao consumidor como 500 — erro
 * dele, não de quem não respondeu.
 * </p>
 * <p>
 * O token técnico é obtido por {@code client_credentials} e mantido em memória até perto do
 * vencimento. Um 401 invalida o cache e a chamada é repetida <strong>uma única vez</strong>: o caso
 * legítimo é o token que venceu entre a checagem e o uso, e repetir indefinidamente transformaria
 * credencial revogada em laço.
 * </p>
 */
public final class KeycloakAdminRestClient implements KeycloakAdminGateway, KeycloakUserLifecycleGateway,
        KeycloakRealmRoleGateway {

    private final RestClient client;
    private final KeycloakAdminProperties properties;
    private volatile Token cachedToken;

    public KeycloakAdminRestClient( RestClient client, KeycloakAdminProperties properties ) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String ensureOrganization( String alias, String displayName ) {
        var existing = organizations( alias ).stream().findFirst();
        if ( existing.isPresent() ) return existing.get().id();
        var location = post( "/admin/realms/{realm}/organizations",
                Map.of( "name", displayName, "alias", alias, "enabled", true ) );
        return idFrom( location );
    }

    @Override
    public void updateOrganization( String organizationId, String displayName ) {
        var path = "/admin/realms/{realm}/organizations/" + organizationId;
        var current = authorizedGet( path, OrganizationRepresentation.class );
        put( path, Map.of( "id", organizationId, "name", displayName,
                "alias", current.alias(), "enabled", current.enabled() ) );
    }

    @Override
    public Optional<User> findUserByEmail( String email ) {
        var result = authorizedGet( queryUri( "/admin/realms/" + properties.realm() + "/users",
                "email=" + encode( email ) + "&exact=true" ), UserRepresentation[].class );
        var users = result == null ? List.<UserRepresentation>of() : Arrays.asList( result );
        if ( users.size() > 1 ) throw new KeycloakAdminException( "buscar conta por e-mail" );
        return users.stream().findFirst().map( UserRepresentation::toUser );
    }

    @Override
    public User createUser( String name, String username, String email, String password ) {
        var parts = name.trim().split( "\\s+", 2 );
        var body = Map.of( "username", username, "email", email, "enabled", true,
                "firstName", parts[0], "lastName", parts.length > 1 ? parts[1] : "" );
        var userId = idFrom( post( "/admin/realms/{realm}/users", body ) );
        try {
            put( "/admin/realms/{realm}/users/" + userId + "/reset-password",
                    Map.of( "type", "password", "value", password, "temporary", false ) );
        } catch ( KeycloakAdminException failure ) {
            try {
                exchangeDelete( "/admin/realms/{realm}/users/" + userId );
            } catch ( KeycloakAdminException compensationFailure ) {
                failure.addSuppressed( compensationFailure );
            }
            throw failure;
        }
        return new User( userId, name, username, email, true );
    }

    @Override
    public Optional<KeycloakUserSnapshot> findUserById( String userId ) {
        try {
            return optionalGet( "/admin/realms/{realm}/users/" + userId, UserRepresentation.class )
                    .map( user -> snapshot( user, listUserGroupIds( userId ) ) );
        } catch ( KeycloakAdminException exception ) {
            throw exception;
        } catch ( RuntimeException exception ) {
            throw new KeycloakAdminException( "consultar conta", exception );
        }
    }

    @Override
    public KeycloakUserSnapshot snapshotUser( String userId ) {
        return findUserById( userId ).orElseThrow( () -> new KeycloakAdminException( "obter retrato de conta" ) );
    }

    @Override
    public void updateUser( KeycloakUserSnapshot user ) {
        put( "/admin/realms/{realm}/users/" + user.id(), userDocument( user ) );
    }

    @Override
    public void setUserEnabled( String userId, boolean enabled ) {
        var user = snapshotUser( userId );
        updateUser( new KeycloakUserSnapshot( user.id(), user.firstName(), user.lastName(), user.username(),
                user.email(), enabled, user.attributes(), user.groupIds() ) );
    }

    @Override
    public void deleteUser( String userId ) {
        exchangeDelete( "/admin/realms/{realm}/users/" + userId );
    }

    @Override
    public Set<String> listUserGroupIds( String userId ) {
        try {
            return getRequiredUserGroups( userId ).stream()
                    .map( GroupRepresentation::id ).collect( java.util.stream.Collectors.toUnmodifiableSet() );
        } catch ( KeycloakAdminException exception ) {
            throw exception;
        } catch ( RuntimeException exception ) {
            throw new KeycloakAdminException( "listar grupos da conta", exception );
        }
    }

    @Override
    public void joinRealmGroup( String userId, String groupId ) {
        try {
            putNoBody( "/admin/realms/{realm}/users/" + userId + "/groups/" + groupId );
        } catch ( KeycloakAdminException exception ) {
            if ( !hasStatus( exception, 409 ) ) throw exception;
        }
    }

    @Override
    public void leaveRealmGroup( String userId, String groupId ) {
        try {
            exchangeDelete( "/admin/realms/{realm}/users/" + userId + "/groups/" + groupId );
        } catch ( KeycloakAdminException exception ) {
            if ( !hasStatus( exception, 404 ) ) throw exception;
        }
    }

    @Override
    public void restoreUser( KeycloakUserSnapshot snapshot ) {
        updateUser( snapshot );
        var current = new HashSet<>( listUserGroupIds( snapshot.id() ) );
        current.stream().filter( groupId -> !snapshot.groupIds().contains( groupId ) )
                .forEach( groupId -> leaveRealmGroup( snapshot.id(), groupId ) );
        snapshot.groupIds().stream().filter( groupId -> !current.contains( groupId ) )
                .forEach( groupId -> joinRealmGroup( snapshot.id(), groupId ) );
    }

    @Override
    public boolean ensureRealmRole( String name, String description ) {
        if ( optionalGet( "/admin/realms/{realm}/roles/" + name, Map.class ).isPresent() ) return false;
        try {
            postNoLocation( "/admin/realms/{realm}/roles", Map.of( "name", name, "description", description ) );
            return true;
        } catch ( KeycloakAdminException exception ) {
            if ( hasStatus( exception, 409 ) ) return false;
            throw exception;
        }
    }

    @Override
    public int ensureCompositeRealmRole( String name, String description, Set<String> directChildren ) {
        ensureRealmRole( name, description );
        var path = "/admin/realms/{realm}/roles/" + name;
        var existing = getMaps( path + "/composites" ).stream()
                .map( role -> String.valueOf( role.get( "name" ) ) ).collect( java.util.stream.Collectors.toSet() );
        var missing = directChildren.stream().filter( child -> !existing.contains( child ) ).toList();
        if ( missing.isEmpty() ) return 0;
        var children = missing.stream().map( child -> getMap( "/admin/realms/{realm}/roles/" + child ) ).toList();
        try {
            postNoLocation( path + "/composites", children );
        } catch ( KeycloakAdminException exception ) {
            if ( hasStatus( exception, 409 ) ) return 0;
            throw exception;
        }
        return missing.size();
    }

    @Override
    public String ensureGroup( String organizationId, String groupName ) {
        var path = "/admin/realms/{realm}/organizations/" + organizationId + "/groups";
        var group = getGroups( path ).stream().filter( item -> groupName.equals( item.name() ) ).findFirst();
        return group.map( GroupRepresentation::id )
                .orElseGet( () -> idFrom( post( path, Map.of( "name", groupName ) ) ) );
    }

    @Override
    public void ensureRealmRoleOnGroup( String organizationId, String groupId, String role ) {
        var roleDocument = getMap( "/admin/realms/{realm}/roles/" + role );
        postNoLocation( "/admin/realms/{realm}/organizations/" + organizationId + "/groups/" + groupId
                + "/role-mappings/realm", List.of( roleDocument ) );
    }

    @Override
    public void addOrganizationMember( String organizationId, String userId ) {
        postNoLocation( "/admin/realms/{realm}/organizations/" + organizationId + "/members", userId );
    }

    @Override
    public void addGroupMember( String organizationId, String groupId, String userId ) {
        putNoBody( "/admin/realms/{realm}/organizations/" + organizationId
                + "/groups/" + groupId + "/members/" + userId );
    }

    @Override
    public void removeGroupMember( String organizationId, String groupId, String userId ) {
        exchangeDelete( "/admin/realms/{realm}/organizations/" + organizationId
                + "/groups/" + groupId + "/members/" + userId );
    }

    @Override
    public List<User> listGroupMembers( String organizationId, String groupId,
            String search, int first, int max ) {
        var path = "/admin/realms/{realm}/organizations/" + organizationId + "/groups/" + groupId
                + "/members?first=" + first + "&max=" + max;
        var normalized = search == null ? "" : search.strip().toLowerCase( Locale.ROOT );
        return getUsers( path ).stream().map( UserRepresentation::toUser )
                .filter( user -> normalized.isBlank()
                        || ( user.name() + " " + user.username() + " " + user.email() )
                                .toLowerCase( Locale.ROOT ).contains( normalized ) )
                .toList();
    }

    private List<OrganizationRepresentation> organizations( String alias ) {
        var result = authorizedGet( queryUri( "/admin/realms/" + properties.realm() + "/organizations",
                "search=" + encode( alias ) ), OrganizationRepresentation[].class );
        return result == null ? List.of() : Arrays.asList( result );
    }

    private List<GroupRepresentation> getGroups( String path ) {
        var result = authorizedGet( path, GroupRepresentation[].class );
        return result == null ? List.of() : Arrays.asList( result );
    }

    private List<GroupRepresentation> getRequiredUserGroups( String userId ) {
        var result = authorizedGet( "/admin/realms/{realm}/users/" + userId + "/groups",
                GroupRepresentation[].class );
        if ( result == null ) throw new KeycloakAdminException( "consultar grupos no Keycloak" );
        return Arrays.asList( result );
    }

    private List<UserRepresentation> getUsers( String path ) {
        var result = authorizedGet( path, UserRepresentation[].class );
        return result == null ? List.of() : Arrays.asList( result );
    }

    private List<Map<String, Object>> getMaps( String path ) {
        var result = authorizedGet( path, Map[].class );
        if ( result == null ) return List.of();
        return Arrays.asList( result );
    }

    @SuppressWarnings( "unchecked" )
    private Map<String, Object> getMap( String path ) {
        return authorizedGet( path, Map.class );
    }

    private <T> T authorizedGet( String path, Class<T> type ) {
        return authorized( "consultar Keycloak", bearer -> client.get().uri( path, properties.realm() )
                .headers( headers -> headers.setBearerAuth( bearer ) ).retrieve().body( type ) );
    }

    private <T> T authorizedGet( URI uri, Class<T> type ) {
        return authorized( "consultar Keycloak", bearer -> client.get().uri( uri )
                .headers( headers -> headers.setBearerAuth( bearer ) ).retrieve().body( type ) );
    }

    private <T> Optional<T> optionalGet( String path, Class<T> type ) {
        for ( var attempt = 0; attempt < 2; attempt++ ) {
            try {
                var body = client.get().uri( path, properties.realm() )
                        .headers( headers -> headers.setBearerAuth( token() ) ).retrieve().body( type );
                if ( body == null ) throw new KeycloakAdminException( "consultar Keycloak" );
                return Optional.of( body );
            } catch ( HttpClientErrorException.Unauthorized exception ) {
                cachedToken = null;
                if ( attempt == 1 ) throw new KeycloakAdminException( "consultar Keycloak", exception );
            } catch ( HttpClientErrorException.NotFound exception ) {
                return Optional.empty();
            } catch ( RuntimeException exception ) {
                throw new KeycloakAdminException( "consultar Keycloak", exception );
            }
        }
        throw new KeycloakAdminException( "consultar Keycloak" );
    }

    private URI post( String path, Object body ) {
        return authorized( "criar recurso no Keycloak", bearer -> client.post().uri( path, properties.realm() )
                .headers( headers -> headers.setBearerAuth( bearer ) ).contentType( MediaType.APPLICATION_JSON )
                .body( body ).retrieve().toBodilessEntity().getHeaders().getLocation() );
    }

    private void postNoLocation( String path, Object body ) {
        authorized( "associar recurso no Keycloak", bearer -> client.post().uri( path, properties.realm() )
                .headers( headers -> headers.setBearerAuth( bearer ) ).contentType( MediaType.APPLICATION_JSON )
                .body( body ).retrieve().toBodilessEntity() );
    }

    private void put( String path, Object body ) {
        authorized( "atualizar recurso no Keycloak", bearer -> client.put().uri( path, properties.realm() )
                .headers( headers -> headers.setBearerAuth( bearer ) ).contentType( MediaType.APPLICATION_JSON )
                .body( body ).retrieve().toBodilessEntity() );
    }

    private void putNoBody( String path ) {
        authorized( "atualizar vinculo no Keycloak", bearer -> client.put().uri( path, properties.realm() )
                .headers( headers -> headers.setBearerAuth( bearer ) ).retrieve().toBodilessEntity() );
    }

    private void exchangeDelete( String path ) {
        authorized( "remover vinculo no Keycloak", bearer -> client.delete().uri( path, properties.realm() )
                .headers( headers -> headers.setBearerAuth( bearer ) ).retrieve().toBodilessEntity() );
    }

    private <T> T authorized( String operation, Function<String, T> request ) {
        for ( var attempt = 0; attempt < 2; attempt++ ) {
            try {
                return request.apply( token() );
            } catch ( HttpClientErrorException.Unauthorized exception ) {
                cachedToken = null;
                if ( attempt == 1 ) throw new KeycloakAdminException( operation, exception );
            } catch ( RuntimeException exception ) {
                throw new KeycloakAdminException( operation, exception );
            }
        }
        throw new KeycloakAdminException( operation );
    }

    private synchronized String token() {
        if ( cachedToken != null && cachedToken.expiresAt().isAfter( Instant.now().plusSeconds( 15 ) ) ) {
            return cachedToken.value();
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add( "grant_type", "client_credentials" );
        form.add( "client_id", properties.clientId() );
        form.add( "client_secret", properties.clientSecret() );
        try {
            var response = client.post()
                    .uri( "/realms/{realm}/protocol/openid-connect/token", properties.realm() )
                    .contentType( MediaType.APPLICATION_FORM_URLENCODED ).body( form )
                    .retrieve().body( TokenResponse.class );
            if ( response == null || response.access_token() == null ) {
                throw new KeycloakAdminException( "obter token tecnico" );
            }
            cachedToken = new Token( response.access_token(),
                    Instant.now().plusSeconds( response.expires_in() ) );
            return cachedToken.value();
        } catch ( KeycloakAdminException exception ) {
            throw exception;
        } catch ( RuntimeException exception ) {
            throw new KeycloakAdminException( "obter token tecnico", exception );
        }
    }

    private static String idFrom( URI location ) {
        if ( location == null ) throw new KeycloakAdminException( "obter identificador externo" );
        var path = location.getPath();
        return path.substring( path.lastIndexOf( '/' ) + 1 );
    }

    private URI queryUri( String path, String query ) {
        var base = properties.baseUrl().endsWith( "/" )
                ? properties.baseUrl().substring( 0, properties.baseUrl().length() - 1 )
                : properties.baseUrl();
        return URI.create( base + path + "?" + query );
    }

    private static String encode( String value ) {
        return URLEncoder.encode( value, StandardCharsets.UTF_8 );
    }

    private static KeycloakUserSnapshot snapshot( UserRepresentation user, Set<String> groups ) {
        return new KeycloakUserSnapshot( user.id(), user.firstName(), user.lastName(), user.username(), user.email(),
                user.enabled(), user.attributes() == null ? Map.of() : user.attributes(), groups );
    }

    private static Map<String, Object> userDocument( KeycloakUserSnapshot user ) {
        var document = new LinkedHashMap<String, Object>();
        document.put( "id", user.id() );
        document.put( "firstName", user.firstName() );
        document.put( "lastName", user.lastName() );
        document.put( "username", user.username() );
        document.put( "email", user.email() );
        document.put( "enabled", user.enabled() );
        document.put( "attributes", user.attributes() );
        return document;
    }

    private static boolean hasStatus( KeycloakAdminException exception, int status ) {
        return exception.getCause() instanceof HttpClientErrorException clientError
                && clientError.getStatusCode().value() == status;
    }

    private record Token(String value, Instant expiresAt) {}

    private record TokenResponse(String access_token, long expires_in) {}

    private record OrganizationRepresentation(String id, String name, String alias, boolean enabled) {}

    private record GroupRepresentation(String id, String name) {}

    private record UserRepresentation(String id, String username, String email,
            String firstName, String lastName, boolean enabled, Map<String, List<String>> attributes) {
        User toUser() {
            return new User( id, ( firstName + " " + lastName ).trim(), username, email, enabled );
        }
    }

}
