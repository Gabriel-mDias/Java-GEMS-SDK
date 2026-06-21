package br.com.gems.mapper.config;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GemsModelMapperAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GemsModelMapperAutoConfiguration.class));

    @Test
    void registersModelMapperBeanByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ModelMapper.class);
            assertThat(context.getBean(ModelMapper.class).getConfiguration().isAmbiguityIgnored()).isTrue();
        });
    }

    @Test
    void backsOffWhenUserProvidesOwnModelMapper() {
        runner.withUserConfiguration(CustomMapperConfig.class).run(context -> {
            assertThat(context).hasSingleBean(ModelMapper.class);
            assertThat(context.getBean(ModelMapper.class)).isSameAs(CustomMapperConfig.CUSTOM);
        });
    }

    static class CustomMapperConfig {
        static final ModelMapper CUSTOM = new ModelMapper();

        @org.springframework.context.annotation.Bean
        ModelMapper modelMapper() {
            return CUSTOM;
        }
    }
}
