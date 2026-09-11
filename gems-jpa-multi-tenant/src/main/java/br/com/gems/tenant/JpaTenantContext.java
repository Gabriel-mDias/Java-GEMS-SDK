package br.com.gems.tenant;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;

/**
 * Contexto em thread-local com a organização da requisição atual, de onde o Hibernate a captura.
 * <p>
 * <strong>Falha fechado.</strong> {@link #getCurrentTenant()} não devolve valor padrão: sem contexto,
 * recusa. Até a 2.0.1 ele devolvia {@code "public"}, e a consequência era silenciosa — código que
 * esquecia de abrir escopo lia e gravava no schema público em vez de emitir erro. Um defeito desses não
 * aparece em teste nem em log; aparece quando um cliente vê o dado de outro.
 * </p>
 * <p>
 * <strong>Dado global tem caminho, e ele é explícito.</strong> Recusar contexto ausente sozinho não
 * bastaria: existe dado legítimo que não pertence a organização alguma — o próprio cadastro de
 * organizações, a identidade, tabelas de referência. Esse tráfego passa por
 * {@link TenantScope#global()}, que instala um escopo global declarado. A diferença é o que importa: a
 * <em>ausência</em> de contexto continua sendo erro, e o global só acontece onde alguém o escreveu.
 * Isso é contável por varredura; um acidente não é.
 * </p>
 *
 * @see TenantScope escopo com fechamento garantido
 * @see TenantContextMissingException a recusa
 */
@Slf4j
public final class JpaTenantContext {

    /**
     * O identificador que o Hibernate recebe quando o escopo é global.
     * <p>
     * Não é nome de schema: o {@link SchemaMultiTenantConnectionProvider} o traduz para
     * {@link TenantSchemaNaming#globalSchema()}. Os underscores duplos e a ausência de significado de
     * negócio são deliberados — é um marcador, e {@link #setCurrentTenant(String)} recusa um alias de
     * organização que tente se passar por ele.
     * </p>
     */
    public static final String GLOBAL_TENANT_IDENTIFIER = "__global__";

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private JpaTenantContext() {
    }

    /**
     * O identificador do tenant da thread atual.
     *
     * @return o alias da organização, ou {@link #GLOBAL_TENANT_IDENTIFIER} em escopo global.
     * @throws TenantContextMissingException se não houver escopo algum instalado (MT-1).
     */
    public static String getCurrentTenant() {
        Snapshot snapshot = CURRENT.get();
        if (snapshot == null) {
            TenantContextMissingException recusa = new TenantContextMissingException(
                    "Operação de persistência sem contexto de tenant. Abra TenantScope.forTenant(alias) "
                            + "para dado de organização, ou TenantScope.global() para dado global.");
            log.error("event=TENANT_CONTEXT_MISSING organizacao={} causa={} modulo=gems-jpa-multi-tenant",
                    TenantLogFields.ORGANIZACAO_AUSENTE, recusa.getMessage());
            throw recusa;
        }
        return snapshot.global() ? GLOBAL_TENANT_IDENTIFIER : snapshot.alias();
    }

    /**
     * O escopo instalado, se houver — sem recusar.
     * <p>
     * É o que colaboradores de infraestrutura usam quando a ausência de contexto é informação, não
     * erro: o decorator de tarefa precisa saber que não há nada a propagar sem provocar MT-1.
     * </p>
     */
    public static Optional<Snapshot> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * Instala o escopo de uma organização na thread atual.
     * <p>
     * Preservada por compatibilidade e para quem gerencia o ciclo de vida por conta própria — um filtro
     * web que limpa no {@code finally}, por exemplo. Onde o escopo tem começo e fim no mesmo bloco,
     * {@link TenantScope} é mais seguro: ele restaura o escopo anterior em vez de simplesmente apagar.
     * </p>
     *
     * @throws IllegalArgumentException se o alias for o marcador reservado de escopo global.
     */
    public static void setCurrentTenant(String alias) {
        String validado = TenantIdentifierValidator.sanitize(alias);
        if (GLOBAL_TENANT_IDENTIFIER.equals(validado)) {
            throw new IllegalArgumentException(
                    "'" + GLOBAL_TENANT_IDENTIFIER + "' é o marcador de escopo global e não pode ser "
                            + "alias de organização. Use TenantScope.global().");
        }
        log.debug("event=TENANT_SCOPE_SET organizacao={} modulo=gems-jpa-multi-tenant", validado);
        CURRENT.set(new Snapshot(validado, false));
    }

    /**
     * Limpa o contexto da thread atual. Obrigatório em pool de threads: escopo que sobrevive à
     * requisição é o mesmo defeito de isolamento, com outro disfarce.
     */
    public static void clear() {
        CURRENT.remove();
    }

    /** Uso interno de {@link TenantScope}: instala e devolve o que restaura o escopo anterior. */
    static Runnable install(Snapshot snapshot) {
        Snapshot anterior = CURRENT.get();
        CURRENT.set(Objects.requireNonNull(snapshot, "snapshot"));
        return () -> {
            if (anterior == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(anterior);
            }
        };
    }

    /**
     * O escopo em vigor.
     *
     * @param alias  alias da organização; {@code null} em escopo global.
     * @param global se o escopo é o global declarado.
     */
    public record Snapshot(String alias, boolean global) {
        public Snapshot {
            if (global == (alias != null)) {
                throw new IllegalArgumentException(
                        "Escopo inválido: ou é global sem alias, ou é de organização com alias.");
            }
        }
    }
}
