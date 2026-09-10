package br.com.gems.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MT-2: o mesmo alias produz o mesmo nome de schema em todos os colaboradores.
 * <p>
 * Este é o teste do defeito dos dois padrões. Ele tem duas metades, e a segunda é a que importa: o
 * defeito original não era dois valores <em>diferentes</em> em uso — era <strong>cada colaborador ter
 * o seu próprio</strong> {@code @Value} com o seu próprio padrão. Enquanto essa estrutura existir,
 * qualquer commit futuro pode reintroduzir a divergência sem quebrar teste algum de comportamento.
 * </p>
 * <p>
 * Por isso a segunda metade varre o código-fonte. É a metade que a mutação T021 derruba.
 * </p>
 */
class SchemaNamingConsistencyTest {

    private static final String PROPRIEDADE_DO_PREFIXO = "gems.tenant.schema-prefix";

    /**
     * O que se procura é a <strong>declaração</strong> da propriedade, não a citação dela.
     * <p>
     * A primeira versão deste teste buscava o nome da propriedade em qualquer posição e reprovou por
     * encontrá-lo no javadoc de {@link TenantSchemaNaming} — que explica justamente por que a
     * propriedade tem uma fonte só. Contar documentação como segunda fonte pressionaria a apagar a
     * explicação para o teste passar, o que é o incentivo exatamente invertido.
     * </p>
     * <p>
     * <strong>A segunda versão exigia o literal {@code @Value("$} e a prova por mutação a
     * derrubou:</strong> declarar {@code @org.springframework.beans.factory.annotation.Value} com o
     * nome qualificado reintroduzia o defeito e o teste seguia verde. Daí o padrão começar em
     * {@code Value("$} — sem o arroba — que casa com as duas formas. Vale registrar porque é o tipo de
     * teste que parece rigoroso e não é: ele estava verificando a <em>grafia</em> da anotação, não a
     * existência de uma segunda fonte.
     * </p>
     */
    private static final String DECLARACAO_DO_PREFIXO = "Value(\"${" + PROPRIEDADE_DO_PREFIXO;

    @Test
    @DisplayName("MT-2: o padrão único é tenant_")
    void oPadraoUnicoEhTenant() {
        assertThat(TenantSchemaNaming.DEFAULT_SCHEMA_PREFIX).isEqualTo("tenant_");
        assertThat(new TenantSchemaNaming(TenantSchemaNaming.DEFAULT_SCHEMA_PREFIX, "").schemaFor("acme"))
                .isEqualTo("tenant_acme");
    }

    @Test
    @DisplayName("MT-2: o alias é normalizado e validado antes de virar nome de schema")
    void aliasEhNormalizadoEValidado() {
        TenantSchemaNaming naming = new TenantSchemaNaming("tenant_", "");

        assertThat(naming.schemaFor("  ACME  ")).isEqualTo("tenant_acme");
    }

    @Test
    @DisplayName("MT-2: a propriedade do prefixo é declarada em exatamente um arquivo de produção")
    void oPrefixoTemUmaFonteSo() throws IOException {
        List<Path> arquivosQueDeclaram = arquivosDeProducao()
                .filter(SchemaNamingConsistencyTest::declaraOPrefixo)
                .toList();

        assertThat(arquivosQueDeclaram)
                .describedAs("A propriedade %s deve ser declarada num lugar só. Um colaborador que "
                        + "volte a declarar o próprio @Value com o próprio padrão reintroduz o defeito "
                        + "de migrar num schema e servir tráfego de outro — que é justamente o que "
                        + "este bloco corrige. Arquivos encontrados: %s",
                        PROPRIEDADE_DO_PREFIXO, arquivosQueDeclaram)
                .hasSize(1);
    }

    private static Stream<Path> arquivosDeProducao() throws IOException {
        // O diretório de trabalho do Surefire é a raiz do módulo.
        return Files.walk(Path.of("src", "main", "java"))
                .filter(Files::isRegularFile)
                .filter(caminho -> caminho.toString().endsWith(".java"));
    }

    private static boolean declaraOPrefixo(Path arquivo) {
        try {
            return Files.readString(arquivo).contains(DECLARACAO_DO_PREFIXO);
        } catch (IOException excecao) {
            throw new IllegalStateException("Falha ao ler " + arquivo, excecao);
        }
    }
}
