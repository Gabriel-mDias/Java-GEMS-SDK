package br.com.gems.tenant.bootstrapfixture;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositório que inicializa sem instalar contexto de tenant fictício. */
public interface DemoRepository extends JpaRepository<DemoRecord, UUID> { }
