package br.com.gems.keycloak.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * KA-3 — a credencial vem de configuração, nunca de código ou de arquivo versionado.
 * <p>
 * As duas primeiras provas cobrem o lado do consumidor: sem segredo configurado a aplicação
 * <strong>não sobe</strong>. As duas últimas cobrem o lado da SDK, e são varreduras de arquivo em vez
 * de asserções sobre objetos — porque o defeito que elas procuram não tem forma de objeto: é alguém
 * digitando um segredo dentro do fonte, ou versionando um {@code application.yml} de conveniência que
 * um dia é copiado para um ambiente real.
 * </p>
 */
class CredentialSourceTest {

    private static final Path FONTES = Path.of( "src", "main", "java" );
    private static final Path RECURSOS = Path.of( "src", "main", "resources" );

    /** Literal atribuído a algo que se chama segredo, senha ou credencial. */
    private static final Pattern SEGREDO_EMBUTIDO = Pattern.compile(
            "(?i)(secret|senha|password|credencial)\\w*\\s*=\\s*\"[^\"]+\"" );

    @Test
    void segredoAusenteImpedeAConstrucao() {
        assertThatThrownBy( () -> propriedadesCom( null ) )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "clientSecret" );
    }

    @Test
    void segredoEmBrancoImpedeAConstrucao() {
        assertThatThrownBy( () -> propriedadesCom( "   " ) )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "clientSecret" );
    }

    @Test
    void nenhumFonteDoModuloEmbuteUmSegredo() throws IOException {
        var infratores = fontes().filter( arquivo -> SEGREDO_EMBUTIDO.matcher( ler( arquivo ) ).find() ).toList();

        assertThat( infratores ).as( "fontes com credencial literal" ).isEmpty();
    }

    @Test
    void oModuloNaoVersionaArquivoDeConfiguracao() throws IOException {
        if ( !Files.isDirectory( RECURSOS ) ) return;

        List<Path> configuracoes;
        try ( Stream<Path> arquivos = Files.walk( RECURSOS ) ) {
            configuracoes = arquivos.filter( Files::isRegularFile )
                    .filter( arquivo -> arquivo.getFileName().toString()
                            .matches( "application.*\\.(ya?ml|properties)" ) )
                    .toList();
        }

        assertThat( configuracoes ).as( "configuração versionada dentro da SDK" ).isEmpty();
    }

    private static KeycloakAdminProperties propriedadesCom( String segredo ) {
        return new KeycloakAdminProperties( "http://keycloak", "realm", "cliente", segredo,
                Duration.ofSeconds( 1 ), Duration.ofSeconds( 1 ) );
    }

    private static Stream<Path> fontes() throws IOException {
        try ( Stream<Path> arquivos = Files.walk( FONTES ) ) {
            return arquivos.filter( Files::isRegularFile )
                    .filter( arquivo -> arquivo.toString().endsWith( ".java" ) )
                    .toList().stream();
        }
    }

    private static String ler( Path arquivo ) {
        try {
            return Files.readString( arquivo, StandardCharsets.UTF_8 );
        } catch ( IOException excecao ) {
            throw new IllegalStateException( "Não foi possível ler " + arquivo, excecao );
        }
    }

}
