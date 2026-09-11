package br.com.gems.exception.handler;

import br.com.gems.exception.config.GemsExceptionAutoConfiguration;
import br.com.gems.exception.exception.handler.AuthorizationExceptionHandler;
import br.com.gems.exception.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EX-2 — quem já tem handler próprio continua com ele depois de atualizar a SDK.
 * <p>
 * O teste olha o <b>contexto</b>, não o código-fonte: a garantia depende de a autoconfiguração
 * entrar pela configuração condicional, e não importar o handler direto. Importado direto, ele é
 * registrado sempre, e o consumidor fica com dois advices concorrendo em ordem que ninguém
 * declarou — que foi exatamente o estado encontrado em 2.0.1.
 * </p>
 */
class HandlerPrecedenceTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration( AutoConfigurations.of( GemsExceptionAutoConfiguration.class ) );

    @Test
    void semHandlerDoConsumidorASdkRegistraOPadrao() {
        contexto.run( ctx -> assertThat( ctx ).hasSingleBean( GlobalExceptionHandler.class ) );
    }

    @Test
    void handlerDoConsumidorMantemPrecedencia() {
        contexto.withUserConfiguration( ConfiguracaoDoConsumidor.class )
                .run( ctx -> {
                    assertThat( ctx ).hasSingleBean( HandlerDoConsumidor.class );
                    assertThat( ctx ).doesNotHaveBean( GlobalExceptionHandler.class );
                } );
    }

    @Test
    void handlerDeAutorizacaoTambemCedeAoConsumidor() {
        contexto.withUserConfiguration( ConfiguracaoDoConsumidor.class )
                .run( ctx -> assertThat( ctx ).doesNotHaveBean( AuthorizationExceptionHandler.class ) );
    }

    @Test
    void comSpringSecurityNoClasspathOHandlerDeAutorizacaoEntra() {
        contexto.run( ctx -> assertThat( ctx ).hasSingleBean( AuthorizationExceptionHandler.class ) );
    }

    @Configuration
    static class ConfiguracaoDoConsumidor {

        @Bean
        HandlerDoConsumidor handlerDoConsumidor() {
            return new HandlerDoConsumidor();
        }

    }

    @RestControllerAdvice
    static class HandlerDoConsumidor {
    }

}
