package br.com.gems.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatorsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    static class Sample {
        @ValidCpf String cpf;
        @ValidCnpj String cnpj;
        @ValidEmail String email;

        Sample(String cpf, String cnpj, String email) {
            this.cpf = cpf;
            this.cnpj = cnpj;
            this.email = email;
        }
    }

    @Test
    void acceptsValidValues() {
        Sample sample = new Sample("52998224725", "11222333000181", "user@example.com");
        assertTrue(validator.validate(sample).isEmpty());
    }

    @Test
    void acceptsNullValues() {
        Sample sample = new Sample(null, null, null);
        assertTrue(validator.validate(sample).isEmpty());
    }

    @Test
    void rejectsInvalidValues() {
        Sample sample = new Sample("12345678900", "00000000000000", "not-an-email");
        assertEquals(3, validator.validate(sample).size());
    }
}
