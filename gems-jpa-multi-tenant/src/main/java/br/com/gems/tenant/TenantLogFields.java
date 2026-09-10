package br.com.gems.tenant;

/**
 * O esquema fixo de log estruturado deste módulo (FR-016).
 * <p>
 * Os campos são sempre os mesmos: {@code event}, {@code organizacao}, {@code causa} e {@code modulo}.
 * O ponto de ter esquema é poder consultar por campo — e um campo que às vezes não aparece derrota
 * isso, porque a consulta que procura recusas deixa de encontrá-las justamente nos casos em que a
 * organização é desconhecida. Daí {@link #ORGANIZACAO_AUSENTE} em vez de omissão.
 * </p>
 * <p>
 * Recusa de contexto e falha de migração saem em <strong>{@code ERROR}</strong>. Não é excesso: chegar
 * ao banco sem escopo é defeito de programação do consumidor, não operação normal, e em {@code WARN}
 * passaria despercebido em ambiente que só alerta em {@code ERROR}. O que está funcionando conforme o
 * contrato é a recusa; o que está errado é a chamada que a provocou.
 * </p>
 * <p>
 * Campos vão como argumentos de log estruturado, e não por MDC: MDC depende de o consumidor propagá-lo
 * por thread, o que este módulo não controla. Nada aqui depende de {@code gems-observability} (FR-017).
 * </p>
 */
public final class TenantLogFields {

    /** O valor do campo {@code organizacao} quando o contexto é justamente o que falta. */
    public static final String ORGANIZACAO_AUSENTE = "<ausente>";

    private TenantLogFields() {
    }
}
