package br.com.gems.firebase.auth;

import java.util.Set;
import java.util.UUID;

/** Seed central separado: exige CRIAR_ADMINISTRADOR_TENANT e destino BOUND, nunca first-login implícito. */
public interface TenantAdministratorBootstrap {
    /** Inicializa/reexecuta atomicamente membership e ações tenant delegáveis, com auditoria do ator central. */
    UUID initialize(UUID tenant, UUID user, Set<String> actions);
}
