package br.com.gems.auditing;

/**
 * Lançada quando a gravação da trilha falha.
 * <p>
 * <strong>Ela sobe, e não é engolida.</strong> Uma trilha que falha em silêncio é pior do que não ter
 * trilha: a operação de negócio é concluída e o registro não existe, então quem consultar mais tarde
 * conclui que nada aconteceu. Como a gravação participa da mesma transação, deixá-la subir desfaz a
 * operação — que é a resposta certa para um sistema que exige auditoria.
 * </p>
 * <p>
 * Consumidor que prefira o contrário — concluir a operação mesmo sem trilha — toma essa decisão
 * explicitamente, tratando esta exceção. O que o módulo não faz é tomá-la por omissão.
 * </p>
 */
public class AuditWriteException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public AuditWriteException(String entity, Throwable causa) {
        super("Falha ao gravar a trilha de auditoria da entidade " + entity, causa);
    }
}
