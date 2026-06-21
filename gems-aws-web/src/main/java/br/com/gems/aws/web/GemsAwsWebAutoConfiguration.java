package br.com.gems.aws.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuração dos endpoints REST do módulo AWS.
 * <p>
 * Registra o {@link S3Controller} apenas em aplicações web servlet (Spring MVC)
 * e quando {@code aws.s3.enabled=true} — mesma flag que ativa os beans de S3 no
 * módulo {@code gems-aws}. Aplicações que só usam o {@code S3Service} programaticamente
 * podem depender apenas de {@code gems-aws} e não incluir este módulo.
 * </p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "aws.s3.enabled", havingValue = "true")
@Import(S3Controller.class)
public class GemsAwsWebAutoConfiguration {
}
