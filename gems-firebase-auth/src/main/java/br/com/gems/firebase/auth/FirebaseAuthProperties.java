package br.com.gems.firebase.auth;

import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Opções opt-in. Emulator exige perfil explicitamente permitido e projeto demo. */
@ConfigurationProperties("gems.firebase.auth")
public record FirebaseAuthProperties(@DefaultValue("false") boolean enabled, String projectId,
        @DefaultValue("true") boolean checkRevoked, @DefaultValue("X-Tenant-Alias") String tenantHeader,
        @DefaultValue("gems-firebase-auth") String appName, @DefaultValue("false") boolean jdbcEnabled,
        @DefaultValue("0") long minimumAppliedMigration, @DefaultValue Emulator emulator) {
    /** O host deve coincidir com FIREBASE_AUTH_EMULATOR_HOST; nenhum fallback automático. */
    public record Emulator(@DefaultValue("false") boolean enabled, String host) { }
    /** Valida mesmo quando o consumidor substitui os adapters. */
    public void validate(String environmentHost, String[] profiles) {
        if (projectId == null || !projectId.matches("[a-z][a-z0-9-]{3,62}") || appName == null || appName.isBlank()
                || "[DEFAULT]".equals(appName) || tenantHeader == null || !tenantHeader.matches("[A-Za-z][A-Za-z0-9-]*")
                || minimumAppliedMigration < 0) {
            throw new IllegalArgumentException("Configuração Firebase inválida");
        }
        boolean hasEnvironment = environmentHost != null && !environmentHost.isBlank();
        if (!emulator.enabled()) {
            if (hasEnvironment || emulator.host() != null || projectId.startsWith("demo-")) {
                throw new IllegalArgumentException("Modo Firebase real inconsistente");
            }
            return;
        }
        Set<String> allowed = Set.of("local", "test", "ci", "demo");
        if (!projectId.startsWith("demo-") || profiles.length == 0
                || !Arrays.stream(profiles).allMatch(allowed::contains)
                || emulator.host() == null || emulator.host().length() > 259
                || !emulator.host().matches("([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*|\\[::1\\]):[0-9]{1,5}")
                || !emulator.host().equals(environmentHost)) {
            throw new IllegalArgumentException("Auth Emulator exige projeto demo, perfil local/test/ci/demo e host explícito");
        }
        int port = Integer.parseInt(emulator.host().substring(emulator.host().lastIndexOf(':') + 1));
        if (port < 1 || port > 65535) { throw new IllegalArgumentException("Porta emulator inválida"); }
    }
}
