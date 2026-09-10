package br.com.gems.auditing;

import java.util.Objects;

/**
 * Quem provocou a alteração.
 * <p>
 * O tipo existe separado do nome porque as duas perguntas que a trilha responde são diferentes: "quem
 * fez" e "isso foi alguém ou foi o sistema". Sem o tipo, distinguir uma rotina automática de um usuário
 * chamado {@code SISTEMA} viraria comparação de string na consulta.
 * </p>
 *
 * @param name nome do autor; {@link #SISTEMA} quando não há usuário autenticado.
 * @param type se a origem é um usuário ou um caminho técnico.
 */
public record AuditActor(String name, AuditActorType type) {

    /** O nome do autor quando a alteração não veio de um usuário autenticado (AU-3). */
    public static final String SISTEMA = "SISTEMA";

    public AuditActor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Nome de autor em branco: use AuditActor.tecnico().");
        }
    }

    /**
     * O autor técnico — a alteração aconteceu sem usuário autenticado.
     * <p>
     * <strong>Isto nunca recusa a operação</strong> (AU-3, decisão 34). Migração, rotina agendada,
     * consumidor de fila e o próprio provisionamento alteram dado legitimamente sem usuário, e uma
     * trilha que recusasse a gravação nesses casos derrubaria operações corretas — ou, pior, levaria
     * alguém a desligar a auditoria para o lote passar.
     * </p>
     */
    public static AuditActor tecnico() {
        return new AuditActor(SISTEMA, AuditActorType.TECNICO);
    }

    /** O autor humano, a partir do nome autenticado. Nome em branco cai no autor técnico. */
    public static AuditActor usuario(String name) {
        return name == null || name.isBlank() ? tecnico() : new AuditActor(name, AuditActorType.USUARIO);
    }
}
