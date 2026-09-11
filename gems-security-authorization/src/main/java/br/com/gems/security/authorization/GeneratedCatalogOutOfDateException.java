package br.com.gems.security.authorization;

/**
 * A representação de frontend não corresponde ao enum que a gera (SA-5).
 * <p>
 * É uma falha de <strong>build</strong>, não de execução: quem a vê é quem editou o arquivo gerado, e
 * a correção é regerar.
 * </p>
 */
public class GeneratedCatalogOutOfDateException extends RuntimeException {

    public GeneratedCatalogOutOfDateException( String message ) {
        super( message );
    }

}
