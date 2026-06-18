package br.com.gems.utils;

import br.com.gems.utils.enums.TipoNumeroDocumentoEnum;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DocumentoUtil {

    public static boolean isDocumentoValid(String numeroDocumento, TipoNumeroDocumentoEnum tipoDocumento) {
        if( ObjectUtil.isNullOrEmpty( numeroDocumento ) ||  ObjectUtil.isNullOrEmpty( tipoDocumento ) ){
            return false;
        }

        return switch (tipoDocumento) {
            case CPF -> isCpfValid(numeroDocumento);
            case CNPJ -> isCnpjValid(numeroDocumento);
            default -> false;
        };
    }

    public static boolean isCpfValid(String cpf) {
        if (ObjectUtil.isNullOrEmpty(cpf)) {
            return false;
        }

        cpf = cpf.replaceAll("\\D", "");

        if (cpf.length() != 11 || cpf.matches("(\\d)\\1{10}")) {
            return false;
        }

        try {
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                sum += (cpf.charAt(i) - '0') * (10 - i);
            }
            int firstDigit = 11 - (sum % 11);
            if (firstDigit > 9) firstDigit = 0;

            if (firstDigit != (cpf.charAt(9) - '0')) {
                return false;
            }

            sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += (cpf.charAt(i) - '0') * (11 - i);
            }
            int secondDigit = 11 - (sum % 11);
            if (secondDigit > 9) secondDigit = 0;

            return secondDigit == (cpf.charAt(10) - '0');
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isCnpjValid(String cnpj) {
        if (ObjectUtil.isNullOrEmpty(cnpj)) {
            return false;
        }

        cnpj = cnpj.replaceAll("\\D", "");

        if (cnpj.length() != 14 || cnpj.matches("(\\d)\\1{13}")) {
            return false;
        }

        try {
            int[] peso1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
            int sum = 0;
            for (int i = 0; i < 12; i++) {
                sum += (cnpj.charAt(i) - '0') * peso1[i];
            }
            int firstDigit = 11 - (sum % 11);
            if (firstDigit >= 10) firstDigit = 0;

            if (firstDigit != (cnpj.charAt(12) - '0')) {
                return false;
            }

            int[] peso2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
            sum = 0;
            for (int i = 0; i < 13; i++) {
                sum += (cnpj.charAt(i) - '0') * peso2[i];
            }
            int secondDigit = 11 - (sum % 11);
            if (secondDigit >= 10) secondDigit = 0;

            return secondDigit == (cnpj.charAt(13) - '0');
        } catch (Exception e) {
            return false;
        }
    }
}
