package br.com.gems.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O resolvedor que o Hibernate consulta.
 * <p>
 * <strong>Um teste desta classe foi substituído nesta rodada, não corrigido.</strong> Havia aqui um
 * {@code resolveCurrentTenantIdentifier_WhenTenantIsNotSet_ReturnsPublic} que afirmava, como
 * comportamento esperado, que contexto ausente devolve {@code "public"} — ou seja, o teste
 * <em>documentava o defeito</em> e o protegia de qualquer correção. Ele passava, e era exatamente por
 * isso que o fail-open sobreviveu a duas versões publicadas.
 * </p>
 */
class TenantIdentifierResolverTest {

    private TenantIdentifierResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TenantIdentifierResolver();
        JpaTenantContext.clear();
    }

    @AfterEach
    void limparContexto() {
        JpaTenantContext.clear();
    }

    @Test
    @DisplayName("Com escopo de organização, devolve o alias ao Hibernate")
    void comEscopoDevolveOAlias() {
        JpaTenantContext.setCurrentTenant("tenant_alpha");

        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("tenant_alpha");
    }

    @Test
    @DisplayName("MT-1: sem escopo, o resolvedor recusa — não devolve padrão algum")
    void semEscopoRecusa() {
        assertThatThrownBy(resolver::resolveCurrentTenantIdentifier)
                .isInstanceOf(TenantContextMissingException.class);
    }

    @Test
    @DisplayName("Em escopo global, devolve o marcador, que o provedor traduz para o schema global")
    void emEscopoGlobalDevolveOMarcador() {
        try (TenantScope escopo = TenantScope.global()) {
            assertThat(resolver.resolveCurrentTenantIdentifier())
                    .isEqualTo(JpaTenantContext.GLOBAL_TENANT_IDENTIFIER);
        }
    }

    @Test
    @DisplayName("validateExistingCurrentSessions segue true")
    void validaSessoesExistentes() {
        assertThat(resolver.validateExistingCurrentSessions()).isTrue();
    }
}
