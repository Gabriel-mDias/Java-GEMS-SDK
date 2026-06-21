package br.com.gems.validation;

import br.com.gems.utils.DocumentoUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador da constraint {@link ValidCpf}, delegando a regra para {@link DocumentoUtil}.
 */
public class CpfValidator implements ConstraintValidator<ValidCpf, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return DocumentoUtil.isCpfValid(value);
    }
}
