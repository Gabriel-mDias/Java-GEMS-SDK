package br.com.gems.jpa.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Map;

/**
 * Interface base customizada para repositórios Spring Data JPA.
 * <p>
 * Oferece métodos auxiliares prontos para execução de consultas HQL e SQL Nativo de forma
 * dinâmica e tipada, simplificando a construção de queries complexas ou paginadas.
 * </p>
 *
 * @param <T> O tipo da Entidade gerenciada pelo repositório.
 */
@NoRepositoryBean
public interface BaseCustomJpaRepository<T> {

    /**
     * Executa uma consulta HQL de contagem (COUNT).
     * @param hql StringBuilder contendo a query HQL de contagem.
     * @return O número total de registros encontrados.
     */
    Long executeCountHql(StringBuilder hql);

    /**
     * Executa uma consulta HQL de contagem com parâmetros.
     * @param hql StringBuilder contendo a query HQL de contagem.
     * @param params Mapa com parâmetros nomeados para injeção na query.
     * @return O número total de registros.
     */
    Long executeCountHql(StringBuilder hql, Map<String, Object> params);

    <R> List<R> executeHql(StringBuilder hql, Class<R> clazz);

    <R> List<R> executeHql(StringBuilder hql, Map<String, Object> params, Class<R> clazz);

    <R> List<R> executeHql(StringBuilder hql, Pageable pageable, Class<R> clazz );

    /**
     * Executa uma consulta HQL paginada e com suporte a projeções via DTOs/Entities.
     *
     * @param hql A query base.
     * @param params Parâmetros nomeados para a query.
     * @param pageable Estrutura de paginação contendo offset e limites.
     * @param clazz A classe ou DTO esperado como retorno na lista.
     * @param <R> O tipo de retorno.
     * @return Uma lista tipada do tamanho da página solicitada.
     */
    <R> List<R> executeHql(StringBuilder hql, Map<String, Object> params, Pageable pageable, Class<R> clazz);

    <R> List<R> executeNativeQuery(StringBuilder sql, Class<R> clazz);

    /**
     * Executa uma query SQL nativa com parâmetros mapeada para um DTO ou Entidade.
     */
    <R> List<R> executeNativeQuery(StringBuilder sql, Map<String, Object> params, Class<R> clazz);

}
