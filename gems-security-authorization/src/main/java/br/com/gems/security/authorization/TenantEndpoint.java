package br.com.gems.security.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endpoint que opera dentro de uma organização.
 * <p>
 * Duas exigências, e elas são independentes: a ação precisa estar no escopo
 * {@link AuthorizationScope#TENANT} do catálogo (verificado por varredura, antes de subir), e a
 * requisição precisa <strong>comprovar</strong> a organização (verificado por
 * {@link TenantAuthorizationInterceptor}, a cada chamada). Ter a ação e não ter a organização é o
 * caso perigoso: a permissão existe, e sem a segunda checagem ela seria exercida sobre a organização
 * de outro.
 * </p>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention( RetentionPolicy.RUNTIME )
public @interface TenantEndpoint {
}
