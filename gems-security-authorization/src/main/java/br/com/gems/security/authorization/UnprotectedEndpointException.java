package br.com.gems.security.authorization;

/**
 * A varredura encontrou endpoint fora da política de autorização (SA-2, SA-3).
 * <p>
 * Como {@link GeneratedCatalogOutOfDateException}, é falha de <strong>build</strong>: o lugar de
 * descobrir que um endpoint subiu desprotegido não é a produção.
 * </p>
 */
public class UnprotectedEndpointException extends RuntimeException {

    public UnprotectedEndpointException( String message ) {
        super( message );
    }

}
