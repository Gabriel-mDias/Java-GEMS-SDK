package br.com.gems.keycloak.admin;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuração do acesso administrativo ao provedor de identidade.
 * <p>
 * <strong>KA-3: nenhum campo tem valor padrão, e o segredo não existe em código.</strong> Um padrão
 * embutido para {@code clientSecret} — ou para {@code baseUrl} — é o que permite uma aplicação subir
 * apontada para o lugar errado sem que ninguém perceba: o campo está preenchido, o build passa, e a
 * descoberta acontece em produção. Aqui a ausência é recusada na construção, então o ambiente que não
 * configurou falha ao subir, que é onde falhar custa menos.
 * </p>
 * <p>
 * O consumidor liga estes valores ao seu mecanismo de configuração ({@code @ConfigurationProperties},
 * variável de ambiente, cofre) — a SDK não escolhe por ele, e não versiona arquivo com segredo.
 * </p>
 */
public record KeycloakAdminProperties(String baseUrl, String realm, String clientId,
        String clientSecret, Duration connectTimeout, Duration readTimeout) {

    public KeycloakAdminProperties {
        baseUrl = obrigatorio( baseUrl, "baseUrl" );
        realm = obrigatorio( realm, "realm" );
        clientId = obrigatorio( clientId, "clientId" );
        clientSecret = obrigatorio( clientSecret, "clientSecret" );
        Objects.requireNonNull( connectTimeout, "connectTimeout" );
        Objects.requireNonNull( readTimeout, "readTimeout" );
    }

    private static String obrigatorio( String valor, String campo ) {
        if ( valor == null || valor.isBlank() ) {
            throw new IllegalArgumentException(
                    "gems-keycloak-admin: '" + campo + "' é obrigatório e vem de configuração, "
                            + "nunca de valor embutido no código." );
        }
        return valor;
    }

}
