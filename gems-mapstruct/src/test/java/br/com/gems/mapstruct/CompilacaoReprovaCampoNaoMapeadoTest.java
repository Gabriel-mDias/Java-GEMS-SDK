package br.com.gems.mapstruct;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MS-2: campo de destino sem origem reprova a compilação, nomeando o campo.
 *
 * <p>Uma reprovação de compilação não pode ser provada pelo próprio build — ele não chegaria a
 * rodar o teste. Então o teste compila, em processo, um mapeador deliberadamente incompleto e
 * inspeciona os diagnósticos do compilador.</p>
 *
 * <p>O controle positivo em {@link #compilacaoCompletaPassaNoMesmoAparato} é o que dá valor ao
 * negativo: sem ele, um aparato quebrado reprovaria tudo e o teste passaria por engano.</p>
 */
class CompilacaoReprovaCampoNaoMapeadoTest {

    private static final Fonte ORIGEM = new Fonte("exemplo.negativo.Origem", """
            package exemplo.negativo;

            public record Origem(String nome) {
            }
            """);

    private static final Fonte DESTINO_COM_CAMPO_SEM_ORIGEM = new Fonte("exemplo.negativo.Destino", """
            package exemplo.negativo;

            public record Destino(String nome, String sobrenome) {
            }
            """);

    private static final Fonte DESTINO_COMPLETO = new Fonte("exemplo.negativo.Destino", """
            package exemplo.negativo;

            public record Destino(String nome) {
            }
            """);

    private static final Fonte MAPEADOR = new Fonte("exemplo.negativo.DestinoMapper", """
            package exemplo.negativo;

            import br.com.gems.mapstruct.GemsMappingConfig;
            import org.mapstruct.Mapper;

            @Mapper(config = GemsMappingConfig.class)
            public interface DestinoMapper {
                Destino toDestino(Origem origem);
            }
            """);

    private static final Fonte MAPEADOR_RELAXADO = new Fonte("exemplo.negativo.DestinoMapper", """
            package exemplo.negativo;

            import br.com.gems.mapstruct.contraste.ConfiguracaoRelaxadaDeContraste;
            import org.mapstruct.Mapper;

            @Mapper(config = ConfiguracaoRelaxadaDeContraste.class)
            public interface DestinoMapper {
                Destino toDestino(Origem origem);
            }
            """);

    @Test
    void campoDeDestinoSemOrigemReprovaACompilacaoENomeiaOCampo(@TempDir Path saida) throws IOException {
        Resultado resultado = compilar(saida, ORIGEM, DESTINO_COM_CAMPO_SEM_ORIGEM, MAPEADOR);

        assertThat(resultado.sucesso())
                .as("a política ERROR precisa reprovar a compilação, não emitir aviso. Diagnósticos: %s",
                        resultado.mensagens())
                .isFalse();
        assertThat(resultado.mensagens())
                .as("o diagnóstico precisa nomear o campo para que o defeito seja acionável")
                .anySatisfy(mensagem -> assertThat(mensagem).contains("sobrenome"));
    }

    /**
     * O contraste que substitui a mutação: mesmo mapeador incompleto, muda só a configuração.
     * Se a política do módulo fosse {@code WARN}, o teste acima ficaria vermelho e este continuaria
     * verde — é a diferença entre os dois que prova quem reprova.
     */
    @Test
    void aMesmaFalhaComPoliticaRelaxadaPassaComAviso(@TempDir Path saida) throws IOException {
        Resultado resultado = compilar(saida, ORIGEM, DESTINO_COM_CAMPO_SEM_ORIGEM, MAPEADOR_RELAXADO);

        assertThat(resultado.sucesso())
                .as("com WARN a compilação passa — e é por isso que a política do módulo é ERROR")
                .isTrue();
        assertThat(resultado.mensagens())
                .as("o campo continua sendo nomeado; muda a severidade, não o diagnóstico")
                .anySatisfy(mensagem -> assertThat(mensagem).contains("sobrenome"));
    }

    @Test
    void compilacaoCompletaPassaNoMesmoAparato(@TempDir Path saida) throws IOException {
        Resultado resultado = compilar(saida, ORIGEM, DESTINO_COMPLETO, MAPEADOR);

        assertThat(resultado.sucesso())
                .as("mapeamento completo precisa passar; senão o aparato reprova por conta própria: %s",
                        resultado.mensagens())
                .isTrue();
    }

    private Resultado compilar(Path saida, Fonte... fontes) throws IOException {
        JavaCompiler compilador = ToolProvider.getSystemJavaCompiler();
        assertThat(compilador)
                .as("os testes exigem um JDK, não apenas um JRE")
                .isNotNull();

        DiagnosticCollector<JavaFileObject> diagnosticos = new DiagnosticCollector<>();
        try (StandardJavaFileManager arquivos =
                     compilador.getStandardFileManager(diagnosticos, null, StandardCharsets.UTF_8)) {

            List<JavaFileObject> unidades = Arrays.stream(fontes)
                    .map(FonteEmMemoria::new)
                    .map(JavaFileObject.class::cast)
                    .toList();

            // O processador é apontado explicitamente. Descoberta por classpath depende de como o
            // executor de teste monta o java.class.path e, dentro do Surefire, simplesmente não
            // acontece: o compilador roda sem processador e não reclama de nada. Um teste que
            // dependesse disso passaria por ausência de erro, provando o contrário do que afirma.
            String classpath = System.getProperty("java.class.path");
            List<String> opcoes = List.of(
                    "-classpath", classpath,
                    "-processorpath", classpath,
                    "-processor", "org.mapstruct.ap.MappingProcessor",
                    "-d", saida.toString());

            boolean sucesso = compilador
                    .getTask(null, arquivos, diagnosticos, opcoes, null, unidades)
                    .call();

            List<String> mensagens = diagnosticos.getDiagnostics().stream()
                    .map(diagnostico -> diagnostico.getMessage(null))
                    .toList();

            return new Resultado(sucesso, mensagens);
        }
    }

    private record Fonte(String nomeQualificado, String codigo) {
    }

    private record Resultado(boolean sucesso, List<String> mensagens) {
    }

    private static final class FonteEmMemoria extends SimpleJavaFileObject {

        private final String codigo;

        private FonteEmMemoria(Fonte fonte) {
            super(URI.create("string:///" + fonte.nomeQualificado().replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.codigo = fonte.codigo();
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return codigo;
        }
    }
}
