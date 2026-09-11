package br.com.gems.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MT-1: contexto ausente recusa a operação.
 * <p>
 * Este é o teste que a mutação T020 tem de derrubar. Se reintroduzir o fallback em
 * {@code getCurrentTenant()} e ele continuar verde, ele não prova nada e é reescrito.
 * </p>
 */
class JpaTenantContextTest {

    @BeforeEach
    @AfterEach
    void limparContexto() {
        JpaTenantContext.clear();
    }

    @Test
    @DisplayName("MT-1: sem escopo instalado, getCurrentTenant recusa em vez de devolver um padrão")
    void contextoAusenteRecusaAOperacao() {
        assertThatThrownBy(JpaTenantContext::getCurrentTenant)
                .isInstanceOf(TenantContextMissingException.class)
                .hasMessageContaining("sem contexto de tenant");
    }

    @Test
    @DisplayName("MT-1: current() informa a ausência sem recusar — é o que a infraestrutura consulta")
    void currentInformaAusenciaSemRecusar() {
        // O decorator de tarefa precisa distinguir "sem escopo" de "erro" para não provocar MT-1 ao
        // apenas verificar se há algo a propagar. Se current() também recusasse, decorar uma tarefa
        // submetida fora de escopo quebraria a submissão.
        assertThat(JpaTenantContext.current()).isEmpty();
    }

    @Test
    @DisplayName("Com escopo de organização, devolve o alias")
    void escopoDeOrganizacaoDevolveOAlias() {
        JpaTenantContext.setCurrentTenant("acme");

        assertThat(JpaTenantContext.getCurrentTenant()).isEqualTo("acme");
    }

    @Test
    @DisplayName("O marcador de escopo global não pode ser usado como alias de organização")
    void marcadorGlobalNaoEhAliasValido() {
        assertThatThrownBy(() -> JpaTenantContext.setCurrentTenant(JpaTenantContext.GLOBAL_TENANT_IDENTIFIER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("marcador de escopo global");
    }

    @Test
    @DisplayName("clear devolve o contexto ao estado de recusa")
    void clearVoltaAoEstadoDeRecusa() {
        JpaTenantContext.setCurrentTenant("acme");
        JpaTenantContext.clear();

        assertThatThrownBy(JpaTenantContext::getCurrentTenant)
                .isInstanceOf(TenantContextMissingException.class);
    }
}
