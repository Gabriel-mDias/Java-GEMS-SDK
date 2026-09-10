package br.com.gems.auditing;

/**
 * O padrão: toda alteração é registrada como técnica.
 * <p>
 * Existe para que o módulo funcione sem configuração alguma, e para que o consumidor que se esqueceu de
 * fornecer o autor descubra isso lendo a trilha — cheia de {@code SISTEMA} — em vez de descobrir por
 * uma falha no arranque. A trilha existir com autor impreciso é melhor do que não existir.
 * </p>
 */
public class SystemAuditActorProvider implements AuditActorProvider {

    @Override
    public AuditActor currentActor() {
        return AuditActor.tecnico();
    }
}
