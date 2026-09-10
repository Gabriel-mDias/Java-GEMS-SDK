package br.com.gems.auditing;

import java.util.Objects;

/**
 * A mudança de um campo, como ela entra na trilha.
 * <p>
 * <strong>O descarte do valor sigiloso acontece aqui, no construtor canônico.</strong> Poderia estar na
 * gravação, e seria pior: um {@code AuditChange} sigiloso carregando os valores existiria em memória,
 * passaria por listeners e por logs de depuração, e bastaria um caminho de gravação novo esquecer o
 * filtro para vazá-lo. Descartando na construção, o objeto sigiloso <em>nunca</em> tem os valores —
 * não há o que esquecer de filtrar depois.
 * </p>
 *
 * @param field    nome da propriedade que mudou.
 * @param oldValue valor anterior, serializado; sempre {@code null} quando {@code sensitive}.
 * @param newValue valor novo, serializado; sempre {@code null} quando {@code sensitive}.
 * @param sensitive se o campo é marcado com {@link SensitiveField}.
 */
public record AuditChange(String field, String oldValue, String newValue, boolean sensitive) {

    public AuditChange {
        Objects.requireNonNull(field, "field");
        if (sensitive) {
            oldValue = null;
            newValue = null;
        }
    }
}
