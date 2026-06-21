package br.com.gems.validation;

import br.com.gems.utils.EmailUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador da constraint {@link ValidEmail}, delegando a regra para {@link EmailUtil}.
 */
public class EmailFormatValidator implements ConstraintValidator<ValidEmail, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return EmailUtil.isValid(value);
    }
}
