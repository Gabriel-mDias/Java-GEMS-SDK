package br.com.gems.auditing;

import java.lang.reflect.Field;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O que entra e o que não entra na trilha.
 * <p>
 * Promovida de {@code meduc-core/auditing/AuditPolicy}. Três regras, e cada uma existe por um motivo
 * que não é óbvio à primeira vista:
 * </p>
 * <ol>
 *   <li><strong>Só campos que realmente mudaram.</strong> O Hibernate marca propriedades como sujas com
 *       folga — reatribuição do mesmo valor conta. Registrar o que ele diz encheria a trilha de
 *       mudanças de nada para nada, e a consulta "o que mudou nesta entidade" deixaria de ser
 *       utilizável.</li>
 *   <li><strong>Campo sigiloso perde os valores</strong> (AU-4), no construtor de {@link AuditChange}.</li>
 *   <li><strong>Valor complexo vira o nome do tipo.</strong> Chamar {@code toString()} numa associação
 *       dispararia carregamento tardio dentro do listener — no meio do flush, com a sessão em estado
 *       delicado — e poderia despejar um grafo inteiro numa coluna de texto.</li>
 * </ol>
 */
public final class AuditPolicy {

    /** Limite da coluna de valor. Texto maior é truncado, não descartado: o começo costuma bastar. */
    public static final int TAMANHO_MAXIMO_DO_VALOR = 4000;

    private AuditPolicy() {
    }

    /**
     * As mudanças que merecem registro, entre o estado anterior e o atual.
     *
     * @param names    nomes das propriedades, na ordem do persister.
     * @param oldState estado anterior; {@code null} em inserção.
     * @param newState estado atual; {@code null} em exclusão.
     * @param dirty    índices que o ORM considera sujos.
     * @param type     a classe da entidade, para encontrar o marcador de sigilo.
     */
    public static List<AuditChange> changedFields(String[] names, Object[] oldState, Object[] newState,
            Collection<Integer> dirty, Class<?> type) {
        List<AuditChange> changes = new ArrayList<>();
        for (Integer index : dirty) {
            Object oldValue = oldState == null ? null : oldState[index];
            Object newValue = newState == null ? null : newState[index];
            if (!Objects.deepEquals(oldValue, newValue)) {
                changes.add(new AuditChange(names[index], serialize(oldValue), serialize(newValue),
                        isSensitive(type, names[index])));
            }
        }
        return List.copyOf(changes);
    }

    /** Se a entidade optou pela trilha. Ver {@link Auditable}. */
    public static boolean isAuditable(Class<?> type) {
        return type != null && type.isAnnotationPresent(Auditable.class);
    }

    /** O valor como ele vai para a coluna de texto da trilha. */
    public static String serialize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof UUID || value instanceof Enum<?> || value instanceof TemporalAccessor) {
            String serialized = value.toString();
            return serialized.length() <= TAMANHO_MAXIMO_DO_VALOR
                    ? serialized
                    : serialized.substring(0, TAMANHO_MAXIMO_DO_VALOR);
        }
        return "<" + value.getClass().getSimpleName() + ">";
    }

    /**
     * Se a propriedade é sigilosa.
     * <p>
     * Procura o campo subindo a hierarquia — entidade de domínio costuma herdar campos de uma base — e,
     * se não achar campo algum, tenta o getter correspondente, porque a propriedade pode estar mapeada
     * por método.
     * </p>
     */
    static boolean isSensitive(Class<?> type, String property) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(property);
                return field.isAnnotationPresent(SensitiveField.class);
            } catch (NoSuchFieldException procuraNaSuperclasse) {
                // Esperado: a propriedade pode estar declarada acima na hierarquia, ou ser mapeada por
                // método. O laço continua; o getter é tentado depois.
            }
        }

        String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        try {
            return type.getMethod("get" + suffix).isAnnotationPresent(SensitiveField.class);
        } catch (NoSuchMethodException semGetter) {
            return false;
        }
    }
}
