package br.com.gems.auditing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um campo cuja <em>mudança</em> deve ser registrada, mas cujos <em>valores</em> não (AU-4).
 * <p>
 * A trilha guarda que o campo mudou, quando e por quem — e descarta o valor anterior e o novo. É o que
 * permite auditar alteração de senha, documento ou dado de saúde sem transformar a própria trilha num
 * repositório de dado sigiloso, que costuma ter controle de acesso mais frouxo que a tabela original,
 * justamente por ser "só log".
 * </p>
 * <p>
 * O descarte acontece na construção de {@link AuditChange}, não na gravação: um valor sigiloso nunca
 * chega a existir num objeto que alguém possa registrar por engano.
 * </p>
 */
@Documented
@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveField {
}
