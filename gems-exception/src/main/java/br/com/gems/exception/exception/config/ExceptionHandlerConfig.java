package br.com.gems.exception.exception.config;

import br.com.gems.exception.exception.handler.AuthorizationExceptionHandler;
import br.com.gems.exception.exception.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Registro dos handlers padrão da SDK.
 * <p>
 * A condição está na <b>classe</b>, não em cada método: avaliada por método, o primeiro bean
 * registrado — que também é um {@code @ControllerAdvice} — desligaria os seguintes, e a
 * configuração se auto-anularia depois do primeiro handler.
 * </p>
 */
@Configuration
@ConditionalOnMissingBean( annotation = ControllerAdvice.class )
public class ExceptionHandlerConfig {

    /**
     * Anotação que permite que os módulos que importem esse, como futuros projetos e
     * etc possam customizar o seu próprio GlobalExceptionHandler, mas se este Bean
     * não for encontrado (por isso o nome de conditional on missing bean), este será
     * a implementação “default” de manipulação de exceções (RestControllerAdvice).
     */
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * Só entra quando o consumidor tem Spring Security no classpath — a dependência é
     * opcional no módulo, e referenciar {@code AccessDeniedException} sem ela quebraria
     * a subida de quem não usa segurança.
     */
    @Configuration
    @ConditionalOnClass( name = "org.springframework.security.access.AccessDeniedException" )
    public static class SecurityHandlerConfig {

        @Bean
        public AuthorizationExceptionHandler authorizationExceptionHandler() {
            return new AuthorizationExceptionHandler();
        }

    }

}
