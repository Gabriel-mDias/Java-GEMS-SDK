package br.com.gems.jpa.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import br.com.gems.jpa.repository.impl.BaseCustomJpaRepositoryImpl;

/**
 * Configuração central do Spring Data JPA.
 * <p>
 * Habilita a varredura de entidades e repositórios a partir dos pacotes definidos,
 * além de registrar o repositório base customizado ({@link BaseCustomJpaRepositoryImpl})
 * em toda a aplicação.
 * </p>
 * <p>
 * Ativada por {@code gems.jpa.enabled=true}. Mantida opcional porque sobrescreve a
 * varredura de entidades/repositórios; aplicações que já declaram seu próprio
 * {@code @EnableJpaRepositories} não devem habilitá-la para evitar conflito.
 * </p>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "gems.jpa.enabled", havingValue = "true")
@EntityScan(basePackages = {"${gems.jpa.base-packages:br.com.gems}"})
@EnableJpaRepositories(
        basePackages = "${gems.jpa.base-packages:br.com.gems}",
        repositoryBaseClass = BaseCustomJpaRepositoryImpl.class
)
public class JpaConfig {

}
