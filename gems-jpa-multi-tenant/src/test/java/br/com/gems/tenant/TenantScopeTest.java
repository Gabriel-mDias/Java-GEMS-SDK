package br.com.gems.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MT-5 e MT-6: o escopo fecha mesmo com exceção, e o global é declarado.
 * <p>
 * O teste de MT-5 é o alvo da mutação T022. Note que ele exercita o caminho de <strong>exceção</strong>:
 * um escopo que só fecha no caminho felizes passa em qualquer teste ingênuo e vaza em produção
 * exatamente quando algo dá errado — que é quando ninguém está olhando o contexto.
 * </p>
 */
class TenantScopeTest {

    @BeforeEach
    @AfterEach
    void limparContexto() {
        JpaTenantContext.clear();
    }

    @Test
    @DisplayName("MT-5: o escopo é limpo mesmo quando o corpo lança")
    void escopoComExcecaoLimpaOContextoMesmoAssim() {
        assertThatThrownBy(() -> TenantScope.runInTenant("acme", () -> {
            assertThat(JpaTenantContext.getCurrentTenant()).isEqualTo("acme");
            throw new IllegalStateException("falha no meio da operação");
        })).hasMessage("falha no meio da operação");

        assertThat(JpaTenantContext.current())
                .describedAs("contexto sobrevivente vaza para a próxima requisição desta thread")
                .isEmpty();
    }

    @Test
    @DisplayName("MT-5: o escopo global também é limpo quando o corpo lança")
    void escopoGlobalComExcecaoLimpaOContexto() {
        assertThatThrownBy(() -> TenantScope.runInGlobal(() -> {
            throw new IllegalStateException("falha no meio da operação global");
        })).hasMessage("falha no meio da operação global");

        assertThat(JpaTenantContext.current()).isEmpty();
    }

    @Test
    @DisplayName("O fechamento restaura o escopo anterior, não apaga o contexto")
    void fechamentoRestauraOEscopoAnterior() {
        try (TenantScope externo = TenantScope.forTenant("acme")) {
            try (TenantScope interno = TenantScope.global()) {
                assertThat(JpaTenantContext.getCurrentTenant())
                        .isEqualTo(JpaTenantContext.GLOBAL_TENANT_IDENTIFIER);
            }

            assertThat(JpaTenantContext.getCurrentTenant())
                    .describedAs("sem restauração, a operação de fora seguiria sem contexto e cairia em MT-1")
                    .isEqualTo("acme");
        }

        assertThat(JpaTenantContext.current()).isEmpty();
    }

    @Test
    @DisplayName("MT-6: o escopo global é o único caminho para dado sem organização")
    void escopoGlobalDeclaradoDevolveOMarcador() {
        String identificador = TenantScope.callInGlobal(JpaTenantContext::getCurrentTenant);

        assertThat(identificador).isEqualTo(JpaTenantContext.GLOBAL_TENANT_IDENTIFIER);
    }

    @Test
    @DisplayName("MT-6: o global não reabre o fail-open — fora dele, a ausência continua recusando")
    void foraDoEscopoGlobalAAusenciaContinuaRecusando() {
        TenantScope.runInGlobal(() -> assertThat(JpaTenantContext.current()).isPresent());

        // As duas metades no mesmo teste são o ponto: o valor de MT-6 depende de MT-1 continuar
        // valendo. Um escopo global que virasse padrão seria o fallback de volta, com outro nome.
        assertThatThrownBy(JpaTenantContext::getCurrentTenant)
                .isInstanceOf(TenantContextMissingException.class);
    }
}
