package br.com.gems.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Configuração dos clientes da AWS S3.
 * <p>
 * As credenciais são opcionais: quando {@code aws.s3.access-key}/{@code aws.s3.secret-key}
 * estão presentes usa credenciais estáticas; caso contrário recai no
 * {@link DefaultCredentialsProvider} (IAM role, variáveis de ambiente, profile, etc.),
 * que é a forma recomendada em produção.
 * </p>
 */
@Configuration
public class AwsS3Config {

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.access-key:#{null}}")
    private String accessKey;

    @Value("${aws.s3.secret-key:#{null}}")
    private String secretKey;

    @Value("${aws.s3.endpoint-url:#{null}}")
    private String endpointUrl;

    private AwsCredentialsProvider credentialsProvider() {
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider());

        if (endpointUrl != null && !endpointUrl.isBlank()) {
            builder.endpointOverride(URI.create(endpointUrl))
                   .forcePathStyle(true); // Necessário para LocalStack
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider());

        if (endpointUrl != null && !endpointUrl.isBlank()) {
            builder.endpointOverride(URI.create(endpointUrl))
                   .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        return builder.build();
    }
}
