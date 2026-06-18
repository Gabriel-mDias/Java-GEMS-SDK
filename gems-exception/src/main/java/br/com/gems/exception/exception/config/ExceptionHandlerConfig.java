package br.com.gems.exception.exception.config;

import br.com.gems.exception.exception.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.ControllerAdvice;

@Configuration
public class ExceptionHandlerConfig {

    /**
     * Anotação que permite que os módulos que importem esse, como futuros projetos e
     * etc possam customizar o seu próprio GlobalExceptionHandler, mas se este Bean
     * não for encontrado (por isso o nome de conditional on missing bean), este será
     * a implementação “default” de manipulação de exceções (RestControllerAdvice).
     */
    @Bean
    @ConditionalOnMissingBean(annotation = ControllerAdvice.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

}
