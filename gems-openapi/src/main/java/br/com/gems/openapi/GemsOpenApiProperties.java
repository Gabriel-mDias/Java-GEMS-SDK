package br.com.gems.openapi;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuração da documentação OpenAPI, sob o prefixo {@code gems.openapi}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gems.openapi")
public class GemsOpenApiProperties {

    /** Habilita a auto-configuração do OpenAPI da GEMS. */
    private boolean enabled = true;

    /** Título exibido na documentação. */
    private String title = "GEMS API";

    /** Descrição da API. */
    private String description = "API documentada com a GEMS SDK";

    /** Versão exibida na documentação. */
    private String version = "v1";

    /** Nome do contato responsável pela API. */
    private String contactName;

    /** E-mail de contato responsável pela API. */
    private String contactEmail;
}
