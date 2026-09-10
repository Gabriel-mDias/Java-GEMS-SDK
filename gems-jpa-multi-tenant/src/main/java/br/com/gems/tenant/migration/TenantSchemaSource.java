package br.com.gems.tenant.migration;

import java.util.Set;

/**
 * Diz ao módulo quais schemas de organização deveriam existir.
 * <p>
 * É o ponto de extensão que permite promover o coordenador de migração sem trazer o modelo de dados do
 * consumidor junto. A implementação de origem consultava o repositório de registro de tenants do
 * Meduc — uma entidade, um enum de estado técnico e uma consulta ordenada que não têm o que fazer numa
 * SDK. Aqui a pergunta é só esta: <em>que schemas devem estar prontos?</em>
 * </p>
 * <p>
 * Quem implementa costuma consultar a própria tabela de organizações, e o faz em escopo global — o
 * cadastro de organizações não pertence a organização alguma. Ver
 * {@link br.com.gems.tenant.TenantScope#callInGlobal}.
 * </p>
 */
public interface TenantSchemaSource {

    /** Os nomes de schema que devem existir e estar migrados. */
    Set<String> expectedSchemas();
}
