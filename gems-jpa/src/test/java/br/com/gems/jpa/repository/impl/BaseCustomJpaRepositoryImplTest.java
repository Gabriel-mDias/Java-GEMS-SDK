package br.com.gems.jpa.repository.impl;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseCustomJpaRepositoryImplTest {

    @Test
    void appendOrderBy_RejectsUnsafeSortProperty() {
        StringBuilder hql = new StringBuilder("SELECT e FROM Entity e");
        Pageable pageable = PageRequest.of(0, 10, Sort.by("name); DROP TABLE users"));

        assertThrows(IllegalArgumentException.class,
                () -> BaseCustomJpaRepositoryImpl.appendOrderByInQuery(hql, pageable));
    }

    @Test
    void appendOrderBy_AppendsSafeSortedProperties() {
        StringBuilder hql = new StringBuilder("SELECT e FROM Entity e");
        Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending().and(Sort.by("createdAt").descending()));

        BaseCustomJpaRepositoryImpl.appendOrderByInQuery(hql, pageable);

        assertEquals("SELECT e FROM Entity e ORDER BY name ASC, createdAt DESC", hql.toString());
    }

    @Test
    void appendOrderBy_AllowsDottedNavigation() {
        StringBuilder hql = new StringBuilder("SELECT e FROM Entity e");
        Pageable pageable = PageRequest.of(0, 10, Sort.by("address.city").ascending());

        BaseCustomJpaRepositoryImpl.appendOrderByInQuery(hql, pageable);

        assertEquals("SELECT e FROM Entity e ORDER BY address.city ASC", hql.toString());
    }

    @Test
    void appendOrderBy_WithoutSort_DoesNothing() {
        StringBuilder hql = new StringBuilder("SELECT e FROM Entity e");

        BaseCustomJpaRepositoryImpl.appendOrderByInQuery(hql, Pageable.ofSize(10));

        assertEquals("SELECT e FROM Entity e", hql.toString());
    }
}
