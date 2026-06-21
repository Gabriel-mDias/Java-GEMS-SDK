package br.com.gems.validation;

import br.com.gems.utils.DocumentoUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador da constraint {@link ValidCnpj}, delegando a regra para {@link DocumentoUtil}.
 */
public class CnpjValidator implements ConstraintValidator<ValidCnpj, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return DocumentoUtil.isCnpjValid(value);
    }
}
