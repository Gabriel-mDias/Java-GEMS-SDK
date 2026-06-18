package br.com.gems.exception.config;

import br.com.gems.exception.exception.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(GlobalExceptionHandler.class)
public class GemsExceptionAutoConfiguration {
}
