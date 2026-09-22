package br.com.gems.auditing;

import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Autoconfiguração da trilha, ativa com {@code gems.auditing.enabled=true}.
 * <p>
 * Mora no mesmo pacote das classes de ligação de propósito: {@code TransactionalAuditWriter}, o
 * listener e o integrador são visíveis só aqui, e é isso que impede um serviço de domínio de alcançar o
 * escritor (AU-6). Pôr a configuração num subpacote {@code config} obrigaria a tornar as três públicas,
 * trocando uma garantia do compilador por uma convenção.
 * </p>
 * <p>
 * Os dois pontos de extensão são {@link ConditionalOnMissingBean}: o consumidor registra o próprio
 * {@link AuditActorProvider} e o próprio {@link AuditTrailDestination} sem tocar no módulo.
 * </p>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "gems.auditing.enabled", havingValue = "true")
public class AuditingAutoConfiguration {

    /** Chave do Hibernate para provedores de integrador. Literal porque a constante mudou de casa entre versões. */
    private static final String INTEGRATOR_PROVIDER = "hibernate.integrator_provider";

    @Bean
    @ConditionalOnMissingBean
    public AuditActorProvider auditActorProvider() {
        return new SystemAuditActorProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditContextProvider auditContextProvider() {
        return new EmptyAuditContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditTrailDestination auditTrailDestination(@Value("${gems.auditing.schema:}") String schema) {
        return new FixedSchemaAuditTrailDestination(schema);
    }

    @Bean
    public HibernatePropertiesCustomizer auditingHibernateCustomizer(AuditActorProvider actors,
            AuditContextProvider contexts, AuditTrailDestination destination,
            @Value("${gems.auditing.context-columns-enabled:false}") boolean contextColumnsEnabled) {
        HibernateAuditListener listener =
                new HibernateAuditListener(new TransactionalAuditWriter(contextColumnsEnabled), actors, contexts,
                        destination);
        Integrator integrator = new AuditHibernateIntegrator(listener);

        return hibernateProperties -> hibernateProperties.put(
                INTEGRATOR_PROVIDER, (IntegratorProvider) () -> List.of(integrator));
    }

    /**
     * Mantém a assinatura pública 3.1.0 para consumidores que instanciam a autoconfiguração em
     * testes ou configuração programática. O comportamento permanece o legado, sem colunas de contexto.
     */
    public HibernatePropertiesCustomizer auditingHibernateCustomizer(AuditActorProvider actors,
            AuditTrailDestination destination) {
        return auditingHibernateCustomizer(actors, new EmptyAuditContextProvider(), destination, false);
    }
}
