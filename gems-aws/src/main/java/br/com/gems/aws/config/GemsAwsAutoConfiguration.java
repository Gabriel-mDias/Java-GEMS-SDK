package br.com.gems.aws.config;

import br.com.gems.aws.service.S3Service;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuração do módulo AWS.
 * <p>
 * Registra explicitamente os beans de infraestrutura ({@link AwsS3Config}) e o serviço
 * de negócio ({@link S3Service}) — sem {@code @ComponentScan}, para não capturar beans
 * inesperados nem colidir com a varredura da aplicação consumidora.
 * </p>
 * <p>Ativada por {@code aws.s3.enabled=true}.</p>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "aws.s3.enabled", havingValue = "true")
@Import({AwsS3Config.class, S3Service.class})
public class GemsAwsAutoConfiguration {
}
