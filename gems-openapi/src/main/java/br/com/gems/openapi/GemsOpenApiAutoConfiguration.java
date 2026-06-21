package br.com.gems.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuração do OpenAPI padrão da GEMS.
 * <p>
 * Cria um bean {@link OpenAPI} com os metadados configurados via {@link GemsOpenApiProperties}
 * ({@code gems.openapi.*}), apenas quando o springdoc está no classpath e
 * {@code gems.openapi.enabled} não está desabilitado. Aplicações que declararem seu próprio
 * {@link OpenAPI} têm precedência ({@code @ConditionalOnMissingBean}).
 * </p>
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnProperty(name = "gems.openapi.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(GemsOpenApiProperties.class)
public class GemsOpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI gemsOpenAPI(GemsOpenApiProperties properties) {
        Info info = new Info()
                .title(properties.getTitle())
                .description(properties.getDescription())
                .version(properties.getVersion());

        if (properties.getContactName() != null || properties.getContactEmail() != null) {
            info.contact(new Contact()
                    .name(properties.getContactName())
                    .email(properties.getContactEmail()));
        }

        return new OpenAPI().info(info);
    }
}
