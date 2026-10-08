package br.com.gems.firebase.auth.consumer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository real descoberto pela auto-configuração Data JPA do Boot. */
public interface BusinessRecordRepository extends JpaRepository<BusinessRecord, UUID> { }
