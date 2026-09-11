package br.com.gems.security.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endpoint acessível sem autenticação.
 * <p>
 * Existe para que "público" seja uma <strong>declaração</strong>, e não a ausência de qualquer
 * declaração: sem ela, o endpoint que alguém esqueceu de proteger e o endpoint que é público de
 * propósito têm exatamente a mesma aparência, e a varredura não pode distingui-los (SA-2).
 * </p>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention( RetentionPolicy.RUNTIME )
public @interface PublicEndpoint {
}
