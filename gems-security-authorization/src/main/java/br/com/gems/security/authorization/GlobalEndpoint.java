package br.com.gems.security.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endpoint que opera sobre dado que não pertence a organização alguma.
 * <p>
 * A ação exigida por {@code @PreAuthorize} precisa estar no escopo
 * {@link AuthorizationScope#GLOBAL} do catálogo.
 * </p>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention( RetentionPolicy.RUNTIME )
public @interface GlobalEndpoint {
}
