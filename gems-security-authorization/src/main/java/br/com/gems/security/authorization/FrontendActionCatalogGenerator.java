package br.com.gems.security.authorization;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deriva do enum de ações a representação que o frontend consome (arbitragem A2).
 * <p>
 * <strong>Por que gerar em vez de conferir.</strong> O frontend precisa da lista de ações localmente,
 * sem chamada de rede — a necessidade é legítima, e foi o que manteve o Meduc com três arquivos em
 * paridade. Mas paridade verificada exige disciplina de quem edita, e disciplina falha em silêncio:
 * quem esquece de atualizar um dos três só descobre quando o gate quebra, ou pior, quando não quebra.
 * Derivar não exige nada de ninguém. A partir daqui a lista do frontend é <em>consequência</em> do
 * enum, e o teste de paridade deixa de proteger a digitação para proteger o gerador.
 * </p>
 * <p>
 * A saída é ordenada e o formato é fixo — sem isso, duas execuções sobre o mesmo enum produziriam
 * arquivos diferentes, e {@link #verify(Path, Class)} acusaria divergência onde não há nenhuma.
 * </p>
 */
public final class FrontendActionCatalogGenerator {

    private FrontendActionCatalogGenerator() {
    }

    /**
     * O documento JSON com as ações do enum: {@code {"global": [...], "tenant": [...]}}, ordenado.
     */
    public static <E extends Enum<E> & AuthorizationAction> String generate( Class<E> actions ) {
        var catalogo = AuthorizationCatalog.of( actions );
        return "{\n"
                + "  \"global\": " + arranjo( catalogo.globalActions() ) + ",\n"
                + "  \"tenant\": " + arranjo( catalogo.tenantActions() ) + "\n"
                + "}\n";
    }

    /** Escreve o documento no destino, criando os diretórios que faltarem. */
    public static <E extends Enum<E> & AuthorizationAction> void writeTo( Path destino, Class<E> actions ) {
        Objects.requireNonNull( destino, "destino" );
        try {
            var pai = destino.toAbsolutePath().getParent();
            if ( pai != null ) Files.createDirectories( pai );
            Files.writeString( destino, generate( actions ), StandardCharsets.UTF_8 );
        } catch ( IOException excecao ) {
            throw new UncheckedIOException( "Não foi possível gerar " + destino, excecao );
        }
    }

    /**
     * Recusa quando o arquivo no destino não é exatamente o que o enum produz — é assim que SA-5 se
     * cumpre: edição à mão da representação gerada não sobrevive ao build.
     *
     * @throws GeneratedCatalogOutOfDateException se o destino não existir ou divergir.
     */
    public static <E extends Enum<E> & AuthorizationAction> void verify( Path destino, Class<E> actions ) {
        Objects.requireNonNull( destino, "destino" );
        var esperado = generate( actions );
        if ( !Files.isRegularFile( destino ) ) {
            throw new GeneratedCatalogOutOfDateException(
                    "A representação de frontend não existe em " + destino
                            + ". Ela é gerada a partir de " + actions.getName() + "." );
        }
        String atual;
        try {
            atual = Files.readString( destino, StandardCharsets.UTF_8 );
        } catch ( IOException excecao ) {
            throw new UncheckedIOException( "Não foi possível ler " + destino, excecao );
        }
        if ( !esperado.equals( normalizar( atual ) ) ) {
            throw new GeneratedCatalogOutOfDateException(
                    "A representação de frontend em " + destino + " diverge de " + actions.getName()
                            + ". Ela é gerada, não editada: regere-a em vez de corrigi-la à mão." );
        }
    }

    /** Quebra de linha do Windows não é divergência de conteúdo. */
    private static String normalizar( String conteudo ) {
        return conteudo.replace( "\r\n", "\n" );
    }

    private static String arranjo( Set<String> acoes ) {
        if ( acoes.isEmpty() ) return "[]";
        var ordenadas = new TreeSet<>( acoes );
        var texto = new StringBuilder( "[\n" );
        var restantes = ordenadas.size();
        for ( var acao : ordenadas ) {
            texto.append( "    \"" ).append( acao ).append( '"' );
            texto.append( --restantes > 0 ? ",\n" : "\n" );
        }
        return texto.append( "  ]" ).toString();
    }

}
