package br.com.gems.utils;

import br.com.gems.utils.enums.TipoNumeroDocumentoEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentoUtilTest {

    @Test
    void isCpfValid_WithValidCpf_ReturnsTrue() {
        assertTrue(DocumentoUtil.isCpfValid("52998224725"));
    }

    @Test
    void isCpfValid_WithValidMaskedCpf_ReturnsTrue() {
        assertTrue(DocumentoUtil.isCpfValid("529.982.247-25"));
    }

    @Test
    void isCpfValid_WithInvalidCheckDigit_ReturnsFalse() {
        assertFalse(DocumentoUtil.isCpfValid("52998224726"));
    }

    @Test
    void isCpfValid_WithRepeatedDigits_ReturnsFalse() {
        assertFalse(DocumentoUtil.isCpfValid("11111111111"));
    }

    @Test
    void isCpfValid_WithWrongLength_ReturnsFalse() {
        assertFalse(DocumentoUtil.isCpfValid("123"));
    }

    @Test
    void isCpfValid_WithNull_ReturnsFalse() {
        assertFalse(DocumentoUtil.isCpfValid(null));
    }

    @Test
    void isCnpjValid_WithValidCnpj_ReturnsTrue() {
        assertTrue(DocumentoUtil.isCnpjValid("11222333000181"));
    }

    @Test
    void isCnpjValid_WithValidMaskedCnpj_ReturnsTrue() {
        assertTrue(DocumentoUtil.isCnpjValid("11.222.333/0001-81"));
    }

    @Test
    void isCnpjValid_WithInvalidCheckDigit_ReturnsFalse() {
        assertFalse(DocumentoUtil.isCnpjValid("11222333000182"));
    }

    @Test
    void isCnpjValid_WithRepeatedDigits_ReturnsFalse() {
        assertFalse(DocumentoUtil.isCnpjValid("00000000000000"));
    }

    @Test
    void isCnpjValid_WithNull_ReturnsFalse() {
        assertFalse(DocumentoUtil.isCnpjValid(null));
    }

    @Test
    void isDocumentoValid_DispatchesByType() {
        assertTrue(DocumentoUtil.isDocumentoValid("52998224725", TipoNumeroDocumentoEnum.CPF));
        assertTrue(DocumentoUtil.isDocumentoValid("11222333000181", TipoNumeroDocumentoEnum.CNPJ));
        assertFalse(DocumentoUtil.isDocumentoValid("52998224725", TipoNumeroDocumentoEnum.CNPJ));
    }

    @Test
    void isDocumentoValid_WithNullArguments_ReturnsFalse() {
        assertFalse(DocumentoUtil.isDocumentoValid(null, TipoNumeroDocumentoEnum.CPF));
        assertFalse(DocumentoUtil.isDocumentoValid("52998224725", null));
    }
}
