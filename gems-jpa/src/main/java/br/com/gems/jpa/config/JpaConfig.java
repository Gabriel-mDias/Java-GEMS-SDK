package br.com.gems.jpa.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import br.com.gems.jpa.repository.impl.BaseCustomJpaRepositoryImpl;

/**
 * Configuração central do Spring Data JPA.
 * <p>
 * Habilita a varredura de entidades e repositórios a partir dos pacotes definidos,
 * além de registrar o repositório base customizado ({@link BaseCustomJpaRepositoryImpl})
 * em toda a aplicação.
 * </p>
 */
@Configuration
@EntityScan(basePackages = {"${gems.jpa.base-packages:br.com.gems}"})
@EnableJpaRepositories(
        basePackages = "${gems.jpa.base-packages:br.com.gems}",
        repositoryBaseClass = BaseCustomJpaRepositoryImpl.class
)
public class JpaConfig {

}
