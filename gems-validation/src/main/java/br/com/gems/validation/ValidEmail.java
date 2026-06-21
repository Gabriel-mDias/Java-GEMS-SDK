package br.com.gems.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Valida que o valor anotado é um e-mail em formato válido.
 * Valores nulos são considerados válidos — combine com {@code @NotNull} se necessário.
 */
@Documented
@Constraint(validatedBy = EmailFormatValidator.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface ValidEmail {

    String message() default "E-mail inválido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
