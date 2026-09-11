package br.com.gems.keycloak.admin;

import br.com.gems.exception.exception.ExternalServiceException;

/**
 * Falha ao administrar o provedor de identidade.
 * <p>
 * <strong>É uma {@link ExternalServiceException}, e é isso que a torna 502.</strong> A origem desta
 * classe no Meduc trazia junto um {@code @RestControllerAdvice} próprio só para devolver
 * {@code BAD_GATEWAY} — mecanismo que {@code gems-exception} passou a oferecer para qualquer serviço
 * fora do processo. Herdar dispensa o handler: o status, o envelope e o log estruturado que nomeia a
 * causa (KA-1 e KA-2) já vêm de lá, e o nome do serviço não vaza para a resposta.
 * </p>
 * <p>
 * A mensagem é <strong>sanitizada por construção</strong>: ela nomeia a operação que falhou, nunca o
 * corpo da resposta do provedor. Repassar aquele corpo ao cliente descreveria a topologia interna sem
 * lhe dar nada acionável — o detalhe fica na causa, que vai para o log.
 * </p>
 */
public class KeycloakAdminException extends ExternalServiceException {

    /** O nome que aparece no log de KA-2. Não é enviado ao cliente. */
    public static final String SERVICO = "keycloak";

    public KeycloakAdminException( String operacao, Throwable causa ) {
        super( SERVICO, mensagem( operacao ), causa );
    }

    public KeycloakAdminException( String operacao ) {
        super( SERVICO, mensagem( operacao ) );
    }

    private static String mensagem( String operacao ) {
        return "Falha sanitizada na integração de identidade durante " + operacao + ".";
    }

}
