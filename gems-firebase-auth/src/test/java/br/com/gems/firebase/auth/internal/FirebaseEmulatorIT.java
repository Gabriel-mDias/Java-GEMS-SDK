package br.com.gems.firebase.auth.internal;

import br.com.gems.firebase.auth.FirebaseAuthException;
import br.com.gems.firebase.auth.FirebaseAuthProperties;
import com.google.api.client.json.gson.GsonFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Gate Auth Emulator demo-gems; unsigned tokens nunca substituem as provas RSA separadas. */
class FirebaseEmulatorIT {
    OwnedFirebaseApp owner;
    FirebaseAdminAdapter adapter;
    String host;
    @BeforeEach void setup() {
        host = System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
        assertThat(host).isEqualTo("127.0.0.1:9099");
        var properties = new FirebaseAuthProperties(true, "demo-gems", false, "X-Tenant-Alias", "emulator-test-" + UUID.randomUUID(), false, 0,
                new FirebaseAuthProperties.Emulator(true, host));
        properties.validate(host, new String[]{"test"}); owner = new OwnedFirebaseApp(properties);
        adapter = new FirebaseAdminAdapter(owner.app(), "demo-gems", false, true);
    }
    @AfterEach void cleanup() { if (owner != null) { owner.close(); } }
    Map<?, ?> request(String route, Map<String, Object> payload) throws Exception {
        return request(route, payload, false);
    }
    Map<?, ?> request(String route, Map<String, Object> payload, boolean administrator) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var builder = HttpRequest.newBuilder(URI.create("http://" + host + "/identitytoolkit.googleapis.com/v1/" + route + "?key=emulator-only"))
                    .header("Content-Type", "application/json").timeout(Duration.ofSeconds(10));
            if (administrator) { builder.header("Authorization", "Bearer owner"); }
            var response = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(GsonFactory.getDefaultInstance().toString(payload))).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return GsonFactory.getDefaultInstance().fromString(response.body(), Map.class);
        }
    }
    String signup() throws Exception {
        return (String) request("accounts:signUp", Map.of("email", UUID.randomUUID() + "@example.test", "password", "emulator-test-only", "returnSecureToken", true)).get("idToken");
    }
    @Test void unsignedTokenDisabledIsRejectedEvenWithCheckRevokedFalse() throws Exception {
        String token = signup(); var identity = adapter.verify(token);
        assertThat(identity.projectId()).isEqualTo("demo-gems");
        adapter.setDisabled(identity.uid(), true);

        assertThatThrownBy(() -> adapter.verify(token)).isInstanceOf(FirebaseAuthException.class).extracting("reason").isEqualTo(FirebaseAuthException.Reason.INVALID_TOKEN);
    }
    @Test void unsignedTokenRevocationIsCheckedEvenWithPropertyFalse() throws Exception {
        String token = signup(); var identity = adapter.verify(token);
        // Revocation timestamps have second precision. Advance the remote validSince deterministically.
        request("accounts:update", Map.of("localId", identity.uid(), "validSince", String.valueOf(identity.authenticatedAt().getEpochSecond() + 1), "targetProjectId", "demo-gems"), true);

        assertThatThrownBy(() -> adapter.verify(token)).isInstanceOf(FirebaseAuthException.class).extracting("reason").isEqualTo(FirebaseAuthException.Reason.INVALID_TOKEN);
    }
    @Test void adminCreatesOnlyPlannedUidAndRepeatsIdempotently() {
        String uid = UUID.randomUUID().toString(); String email = uid + "@example.test";
        assertThat(adapter.createUser(uid, email, "Person").uid()).isEqualTo(uid);
        assertThat(adapter.createUser(uid, email, "Person").uid()).isEqualTo(uid);
        adapter.setDisabled(uid, true); assertThat(adapter.getUser(uid).disabled()).isTrue();
        adapter.setDisabled(uid, false); assertThat(adapter.getUser(uid).disabled()).isFalse();
    }
    @Test void malformedTokenIs401() {
        assertThatThrownBy(() -> adapter.verify("invalid")).isInstanceOf(FirebaseAuthException.class).extracting("reason").isEqualTo(FirebaseAuthException.Reason.INVALID_TOKEN);
    }
    @Test void realAdminGatewayRevokesOldTokenEvenAfterExplicitEnable() throws Exception {
        String token = signup(); var identity = adapter.verify(token);
        // Admin usa precisão de segundos; esperar a fronteira evita revogar no mesmo segundo do auth_time.
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (java.time.Instant.now().getEpochSecond() <= identity.authenticatedAt().getEpochSecond()) {
            assertThat(System.nanoTime()).isLessThan(deadline); Thread.sleep(25);
        }
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(br.com.gems.firebase.auth.config.FirebaseAuthAutoConfiguration.class))
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .withPropertyValues("gems.firebase.auth.enabled=true", "gems.firebase.auth.project-id=demo-gems", "gems.firebase.auth.emulator.enabled=true", "gems.firebase.auth.emulator.host=" + host)
                .withBean(com.google.firebase.FirebaseApp.class, owner::app)
                .withBean(br.com.gems.security.authorization.AuthorizationCatalog.class, () -> new br.com.gems.security.authorization.AuthorizationCatalog(java.util.Set.of(), java.util.Set.of()))
                .withBean(br.com.gems.firebase.auth.IdTokenVerifier.class, () -> adapter::verify)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var gateway = context.getBean(br.com.gems.firebase.auth.FirebaseAdminGateway.class);
                    gateway.setDisabled(identity.uid(), true); gateway.revokeRefreshTokens(identity.uid()); gateway.setDisabled(identity.uid(), false);
                });
        assertThatThrownBy(() -> adapter.verify(token)).isInstanceOf(FirebaseAuthException.class)
                .extracting("reason").isEqualTo(FirebaseAuthException.Reason.INVALID_TOKEN);
    }
}
