package br.com.gems.tenant;

import java.util.function.Supplier;

/**
 * Escopo de tenant com fechamento garantido.
 * <p>
 * Existe porque {@link JpaTenantContext#setCurrentTenant(String)} e
 * {@link JpaTenantContext#clear()} deixam o fechamento por conta de quem chama, e o caminho de exceção
 * é exatamente onde essa disciplina falha. Escopo que não fecha em thread de pool vaza para a próxima
 * requisição servida por aquela thread — que passa a ler o dado de outra organização sem que nada
 * aconteça de errado do ponto de vista do código.
 * </p>
 * <p>
 * Duas garantias, e a segunda é a menos óbvia:
 * </p>
 * <ol>
 *   <li>o escopo fecha mesmo que o corpo lance (MT-5) — daí o {@code finally} em cada método aqui;</li>
 *   <li>o fechamento <strong>restaura o escopo anterior</strong> em vez de apagar o contexto. Sem isso,
 *       um escopo global aberto no meio de uma operação de organização deixaria a operação de fora
 *       rodando sem contexto ao voltar, e ela cairia em MT-1 por culpa do aninhamento.</li>
 * </ol>
 *
 * <pre>{@code
 * // dado de uma organização
 * try (TenantScope escopo = TenantScope.forTenant("acme")) {
 *     repositorio.save(matricula);
 * }
 *
 * // dado que não pertence a organização alguma
 * var organizacoes = TenantScope.callInGlobal(organizacaoRepository::findAll);
 * }</pre>
 */
public final class TenantScope implements AutoCloseable {

    private final Runnable restauracao;

    private TenantScope(Runnable restauracao) {
        this.restauracao = restauracao;
    }

    /** Abre o escopo de uma organização. O alias é validado. */
    public static TenantScope forTenant(String alias) {
        String validado = TenantIdentifierValidator.sanitize(alias);
        if (JpaTenantContext.GLOBAL_TENANT_IDENTIFIER.equals(validado)) {
            throw new IllegalArgumentException(
                    "'" + JpaTenantContext.GLOBAL_TENANT_IDENTIFIER
                            + "' é o marcador de escopo global e não pode ser alias. Use TenantScope.global().");
        }
        return new TenantScope(JpaTenantContext.install(new JpaTenantContext.Snapshot(validado, false)));
    }

    /**
     * Abre o escopo global — o único caminho para dado que não pertence a organização alguma (MT-6).
     * <p>
     * Cada chamada é uma declaração auditável de que aquele acesso é global de propósito. É a diferença
     * entre isso e o valor padrão que este módulo tinha: dá para contar as chamadas e revisá-las uma a
     * uma; não dá para contar um esquecimento.
     * </p>
     */
    public static TenantScope global() {
        return new TenantScope(JpaTenantContext.install(new JpaTenantContext.Snapshot(null, true)));
    }

    /** Executa em escopo de organização, fechando mesmo que o corpo lance. */
    public static void runInTenant(String alias, Runnable trabalho) {
        TenantScope escopo = forTenant(alias);
        try {
            trabalho.run();
        } finally {
            escopo.close();
        }
    }

    /** Executa em escopo global, fechando mesmo que o corpo lance. */
    public static void runInGlobal(Runnable trabalho) {
        TenantScope escopo = global();
        try {
            trabalho.run();
        } finally {
            escopo.close();
        }
    }

    /** Calcula em escopo de organização, fechando mesmo que o corpo lance. */
    public static <T> T callInTenant(String alias, Supplier<T> trabalho) {
        TenantScope escopo = forTenant(alias);
        try {
            return trabalho.get();
        } finally {
            escopo.close();
        }
    }

    /** Calcula em escopo global, fechando mesmo que o corpo lance. */
    public static <T> T callInGlobal(Supplier<T> trabalho) {
        TenantScope escopo = global();
        try {
            return trabalho.get();
        } finally {
            escopo.close();
        }
    }

    /** Restaura o escopo que existia antes desta abertura. Idempotente não é: feche uma vez. */
    @Override
    public void close() {
        restauracao.run();
    }
}
