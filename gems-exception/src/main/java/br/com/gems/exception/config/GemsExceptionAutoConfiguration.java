package br.com.gems.exception.config;

import br.com.gems.exception.exception.config.ExceptionHandlerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Importa a configuração, não o handler.
 * <p>
 * Importar {@code GlobalExceptionHandler} diretamente registrava-o <b>sempre</b>, e o
 * {@code @ConditionalOnMissingBean} escrito em {@link ExceptionHandlerConfig} nunca era avaliado:
 * o consumidor com handler próprio ficava com dois advices concorrendo em ordem indeterminada.
 * Entrar pela configuração é o que faz EX-2 valer — o handler do consumidor vence.
 * </p>
 */
@AutoConfiguration
@Import(ExceptionHandlerConfig.class)
public class GemsExceptionAutoConfiguration {
}
