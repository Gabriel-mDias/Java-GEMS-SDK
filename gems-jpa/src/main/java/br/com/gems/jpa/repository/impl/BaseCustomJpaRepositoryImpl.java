package br.com.gems.jpa.repository.impl;

import br.com.gems.jpa.repository.BaseCustomJpaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.util.List;
import java.util.Map;

public class BaseCustomJpaRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> implements BaseCustomJpaRepository<T> {

    private final EntityManager entityManager;

    public BaseCustomJpaRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    @Override
    public Long executeCountHql(StringBuilder hql) {
        return entityManager.createQuery(hql.toString(), Long.class).getSingleResult();
    }

    @Override
    public Long executeCountHql(StringBuilder hql, Map<String, Object> params) {
        var query = entityManager.createQuery(hql.toString(), Long.class);
        params.forEach(query::setParameter);
        return query.getSingleResult();
    }

    @Override
    public <R> List<R> executeHql(StringBuilder hql, Class<R> clazz) {
        return entityManager.createQuery(hql.toString(), clazz).getResultList();
    }

    @Override
    public <R> List<R> executeHql(StringBuilder hql, Map<String, Object> params, Class<R> clazz) {
        var query = entityManager.createQuery(hql.toString(), clazz);
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    @Override
    public <R> List<R> executeHql(StringBuilder hql, Pageable pageable, Class<R> clazz) {
        appendOrderByInQuery(hql, pageable);

        var query = entityManager.createQuery(hql.toString(), clazz);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return query.getResultList();
    }

    @Override
    public <R> List<R> executeHql(StringBuilder hql, Map<String, Object> params, Pageable pageable, Class<R> clazz) {
        appendOrderByInQuery(hql, pageable);

        var query = entityManager.createQuery(hql.toString(), clazz);
        params.forEach(query::setParameter);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return query.getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> List<R> executeNativeQuery(StringBuilder sql, Class<R> clazz) {
        return (List<R>) entityManager.createNativeQuery(sql.toString(), clazz).getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> List<R> executeNativeQuery(StringBuilder sql, Map<String, Object> params, Class<R> clazz) {
        var query = entityManager.createNativeQuery(sql.toString(), clazz);
        params.forEach(query::setParameter);
        return (List<R>) query.getResultList();
    }

    private void appendOrderByInQuery(StringBuilder hql, Pageable pageable) {
        if (!pageable.getSort().isSorted()) {
            return;
        }

        hql.append( " ORDER BY " );
        pageable.getSort().forEach(order -> {
            hql.append( order.getProperty() );
            hql.append( " " );
            hql.append( order.getDirection().name() );
            hql.append( ", " );
        } );

        //Removendo os últimos 2 caracteres (", ")
        hql.delete( hql.length() - 2, hql.length() );
    }

}
