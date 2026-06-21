package br.com.gems.observability;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuração de observabilidade da GEMS.
 * <p>
 * Registra um {@link ObservedAspect}, habilitando a anotação {@code @Observed} (Micrometer)
 * em métodos de beans Spring para gerar métricas/traces de forma declarativa. Só é ativada
 * quando existe um {@link ObservationRegistry} no contexto (normalmente provido pelo Actuator)
 * e quando {@code gems.observability.enabled} não está desabilitado.
 * </p>
 */
@AutoConfiguration
@ConditionalOnClass({ObservedAspect.class, ObservationRegistry.class})
@ConditionalOnProperty(name = "gems.observability.enabled", havingValue = "true", matchIfMissing = true)
public class GemsObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnBean(ObservationRegistry.class)
    @ConditionalOnMissingBean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
