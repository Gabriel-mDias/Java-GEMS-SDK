package br.com.gems.auditing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opta a entidade para a trilha de auditoria (AU-1).
 * <p>
 * <strong>A trilha é opt-in, e isso é deliberado.</strong> Auditar tudo por padrão parece mais seguro
 * e não é: a trilha cresce com tabelas de referência, de fila e de importação, que ninguém consulta e
 * que enterram o registro que importa. Pior, numa biblioteca isso seria decisão da SDK sobre o custo de
 * armazenamento do consumidor. Marcar a entidade é barato e diz explicitamente o que é digno de
 * registro.
 * </p>
 * <p>
 * A anotação é {@link Inherited}: uma superclasse marcada cobre as subclasses, que é o caso comum de
 * uma entidade-base de domínio.
 * </p>
 *
 * @see SensitiveField para registrar a mudança de um campo sem os valores dele
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
}
