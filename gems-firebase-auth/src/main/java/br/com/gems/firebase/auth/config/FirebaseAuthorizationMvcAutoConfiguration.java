package br.com.gems.firebase.auth.config;

import br.com.gems.security.authorization.AuthorizationCatalog;
import br.com.gems.security.authorization.EndpointAuthorizationScan;
import br.com.gems.security.authorization.TenantAuthorizationInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Integração canônica de classification/scanner/interceptor, sem chain própria. */
@AutoConfiguration(after = FirebaseServletAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "gems.firebase.auth", name = "enabled", havingValue = "true")
public class FirebaseAuthorizationMvcAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    public TenantAuthorizationInterceptor firebaseTenantAuthorizationInterceptor() { return new TenantAuthorizationInterceptor(); }
    @Bean
    public WebMvcConfigurer firebaseAuthorizationMvcConfigurer(TenantAuthorizationInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(interceptor); }
        };
    }
    @Bean
    public SmartInitializingSingleton firebaseEndpointAuthorizationScan(ObjectProvider<RequestMappingHandlerMapping> mappings,
            AuthorizationCatalog catalog) {
        return () -> mappings.orderedStream().forEach(mapping -> EndpointAuthorizationScan.assertProtected(
                mapping.getHandlerMethods().values().stream()
                        // Apenas o handler de erro do framework; endpoints do consumidor não recebem exceção.
                        .filter(method -> !method.getBeanType().getName().equals("org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController"))
                        .toList(), catalog));
    }
}
