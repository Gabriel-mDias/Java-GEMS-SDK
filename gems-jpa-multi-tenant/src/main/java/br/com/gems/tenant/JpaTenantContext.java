package br.com.gems.tenant;

import lombok.extern.slf4j.Slf4j;

/**
 * Contexto em thread-local responsável por armazenar a identificação do tenant 
 * da requisição atual de forma global para que o Hibernate possa capturá-la.
 */
@Slf4j
public class JpaTenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    public static final String DEFAULT_TENANT = "public";

    /**
     * Retorna o tenant associado à thread atual.
     * @return O identificador do tenant, ou o tenant default (public) caso não haja nenhum setado.
     */
    public static String getCurrentTenant() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }

    /**
     * Define o tenant para a thread de requisição atual.
     * @param tenant O identificador (acronym) do tenant.
     */
    public static void setCurrentTenant(String tenant) {
        log.debug("Setting tenant to " + tenant);
        CURRENT_TENANT.set(tenant);
    }

    /**
     * Limpa o contexto do tenant na thread atual. Importante para evitar memory leaks 
     * em pools de threads como os usados em servidores de aplicação web.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
