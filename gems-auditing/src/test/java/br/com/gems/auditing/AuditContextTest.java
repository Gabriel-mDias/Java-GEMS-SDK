package br.com.gems.auditing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditContextTest {

    @Test
    @DisplayName("Contexto vazio não inventa autor nem correlação")
    void contextoVazioNaoInventaValores() {
        assertThat(AuditContext.empty())
                .isEqualTo(new AuditContext(null, null));
    }

    @Test
    @DisplayName("Valores em branco são normalizados para ausentes")
    void valoresEmBrancoSaoNormalizadosParaAusentes() {
        AuditContext context = new AuditContext("  ", "\t");

        assertThat(context.actorId()).isNull();
        assertThat(context.correlationId()).isNull();
    }

    @Test
    @DisplayName("Valores presentes são preservados")
    void valoresPresentesSaoPreservados() {
        AuditContext context = new AuditContext("usuario-42", "correlacao-42");

        assertThat(context.actorId()).isEqualTo("usuario-42");
        assertThat(context.correlationId()).isEqualTo("correlacao-42");
    }
}
