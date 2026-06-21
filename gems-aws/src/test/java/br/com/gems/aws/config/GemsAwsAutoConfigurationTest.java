package br.com.gems.aws.config;

import br.com.gems.aws.service.S3Service;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GemsAwsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GemsAwsAutoConfiguration.class));

    @Test
    void doesNotRegisterBeansWhenDisabled() {
        runner.run(context -> assertThat(context).doesNotHaveBean(S3Service.class));
    }

    @Test
    void registersS3ServiceWhenEnabled() {
        runner.withPropertyValues("aws.s3.enabled=true", "aws.s3.region=us-east-1")
                .run(context -> assertThat(context).hasSingleBean(S3Service.class));
    }
}
