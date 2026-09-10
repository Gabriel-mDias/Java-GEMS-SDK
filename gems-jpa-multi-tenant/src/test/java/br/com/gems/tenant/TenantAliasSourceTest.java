package br.com.gems.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MT-8: fontes de alias divergentes recusam a operação.
 */
class TenantAliasSourceTest {

    /** Fonte de teste com nome próprio, porque o log de conflito precisa distinguir duas instâncias. */
    private record FonteFixa(String nome, String alias) implements TenantAliasSource {

        @Override
        public Optional<String> currentAlias() {
            return Optional.ofNullable(alias);
        }

        @Override
        public String sourceName() {
            return nome;
        }
    }

    @Test
    @DisplayName("MT-8: duas fontes com aliases diferentes recusam, e o erro nomeia as duas")
    void fontesDivergentesRecusamENomeiamAsDuas() {
        TenantAliasResolver resolver = new TenantAliasResolver(List.of(
                new FonteFixa("ClaimDoJwt", "acme"),
                new FonteFixa("Subdominio", "globex")));

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(TenantAliasConflictException.class)
                .hasMessageContaining("ClaimDoJwt=acme")
                .hasMessageContaining("Subdominio=globex");
    }

    @Test
    @DisplayName("Fontes que concordam não conflitam — redundância deliberada é o caso comum")
    void fontesQueConcordamNaoConflitam() {
        TenantAliasResolver resolver = new TenantAliasResolver(List.of(
                new FonteFixa("ClaimDoJwt", "acme"),
                new FonteFixa("Subdominio", "ACME")));

        assertThat(resolver.resolve()).contains("acme");
    }

    @Test
    @DisplayName("Fonte sem opinião não vota, e não impede as demais")
    void fonteSemOpiniaoNaoVota() {
        TenantAliasResolver resolver = new TenantAliasResolver(List.of(
                new FonteFixa("Cabecalho", null),
                new FonteFixa("ClaimDoJwt", "acme")));

        assertThat(resolver.resolve()).contains("acme");
    }

    @Test
    @DisplayName("Sem fonte alguma, devolve vazio — cabe a quem chama decidir se isso é erro")
    void semFonteAlgumaDevolveVazio() {
        assertThat(new TenantAliasResolver(List.of()).resolve()).isEmpty();
    }
}
