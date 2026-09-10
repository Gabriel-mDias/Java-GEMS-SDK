package br.com.gems.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SA-5 — a representação de frontend é derivada do enum, e edição à mão nela não sobrevive. */
class FrontendActionCatalogGeneratorTest {

    /**
     * O arquivo versionado que representa o que o frontend consumiria. É sobre ele que a mutação 3 do
     * contrato acontece: editá-lo à mão precisa deixar {@link #representacaoVersionadaCorrespondeAoEnum()}
     * vermelho.
     */
    private static final Path VERSIONADA = Path.of( "src", "test", "resources", "authorization-actions.json" );

    @Test
    void geraOsDoisEscoposEmOrdemEstavel() {
        var documento = FrontendActionCatalogGenerator.generate( AcaoDeTeste.class );

        assertThat( documento ).isEqualTo( """
                {
                  "global": [
                    "ALTERAR_ORGANIZACAO",
                    "CONSULTAR_ORGANIZACAO"
                  ],
                  "tenant": [
                    "CONSULTAR_TURMA"
                  ]
                }
                """ );
    }

    /**
     * Sem ordem estável, duas gerações do mesmo enum produziriam arquivos diferentes e
     * {@code verify} acusaria divergência onde não existe nenhuma — a verificação viraria ruído e
     * seria desligada.
     */
    @Test
    void duasGeracoesDoMesmoEnumProduzemOMesmoTexto() {
        assertThat( FrontendActionCatalogGenerator.generate( AcaoDeTeste.class ) )
                .isEqualTo( FrontendActionCatalogGenerator.generate( AcaoDeTeste.class ) );
    }

    @Test
    void representacaoVersionadaCorrespondeAoEnum() {
        assertThatCode( () -> FrontendActionCatalogGenerator.verify( VERSIONADA, AcaoDeTeste.class ) )
                .doesNotThrowAnyException();
    }

    /** SA-5, o caminho do defeito: alguém acrescenta uma ação no arquivo em vez de no enum. */
    @Test
    void representacaoEditadaAMaoDiverge( @TempDir Path diretorio ) throws IOException {
        var destino = diretorio.resolve( "authorization-actions.json" );
        FrontendActionCatalogGenerator.writeTo( destino, AcaoDeTeste.class );
        var editado = Files.readString( destino, StandardCharsets.UTF_8 )
                .replace( "\"CONSULTAR_TURMA\"", "\"CONSULTAR_TURMA\",\n    \"INVENTADA_AQUI\"" );
        Files.writeString( destino, editado, StandardCharsets.UTF_8 );

        assertThatThrownBy( () -> FrontendActionCatalogGenerator.verify( destino, AcaoDeTeste.class ) )
                .isInstanceOf( GeneratedCatalogOutOfDateException.class )
                .hasMessageContaining( "diverge" );
    }

    @Test
    void representacaoAusenteReprova( @TempDir Path diretorio ) {
        assertThatThrownBy( () -> FrontendActionCatalogGenerator.verify(
                diretorio.resolve( "nao-existe.json" ), AcaoDeTeste.class ) )
                .isInstanceOf( GeneratedCatalogOutOfDateException.class )
                .hasMessageContaining( "não existe" );
    }

    @Test
    void escreverCriaOsDiretoriosQueFaltam( @TempDir Path diretorio ) {
        var destino = diretorio.resolve( "core" ).resolve( "authorization" ).resolve( "acoes.json" );

        FrontendActionCatalogGenerator.writeTo( destino, AcaoDeTeste.class );

        assertThat( destino ).exists();
    }

}
