package br.com.gems.firebase.auth.internal;

import br.com.gems.firebase.auth.FirebaseAuthException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Verifica RSA e lookup Admin reais contra transporte fechado; nenhuma URL externa é executada. */
class FirebaseRsaVerificationTest {
    KeyPair pair;
    String certificate;
    FirebaseApp app;
    final AtomicInteger lookups = new AtomicInteger();
    boolean disabled;
    long validSince;
    boolean outage;
    boolean lookupOutage;
    final long now = Instant.now().getEpochSecond();
    @BeforeEach void prepare() throws Exception {
        assertThat(System.getenv("FIREBASE_AUTH_EMULATOR_HOST")).isNull();
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); pair = generator.generateKeyPair();
        var name = new X500Name("CN=gems-test-only");
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                Date.from(Instant.now().minusSeconds(86400)), Date.from(Instant.now().plusSeconds(86400)), name, pair.getPublic());
        var encoded = builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())).getEncoded();
        certificate = "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(encoded) + "\n-----END CERTIFICATE-----\n";
        HttpTransport transport = new HttpTransport() {
            @Override protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
                String body;
                if (url.contains("/metadata/x509/securetoken@system.gserviceaccount.com")) {
                    body = GsonFactory.getDefaultInstance().toString(Map.of("test-key", certificate));
                } else if (url.contains("identitytoolkit.googleapis.com") && url.contains("accounts:lookup")) {
                    lookups.incrementAndGet();
                    body = "{\"users\":[{\"localId\":\"uid\",\"email\":\"person@example.test\",\"emailVerified\":true,\"disabled\":"
                            + disabled + ",\"validSince\":\"" + validSince + "\"}]}";
                } else { throw new IOException("UNEXPECTED_HTTP_STUB_REQUEST"); }
                return new MockLowLevelHttpRequest(url).setResponse(new MockLowLevelHttpResponse()
                        .setStatusCode(outage || (lookupOutage && url.contains("accounts:lookup")) ? 503 : 200).setContentType("application/json")
                        .addHeader("Cache-Control", "public,max-age=3600").setContent(outage || (lookupOutage && url.contains("accounts:lookup")) ? "{}" : body));
            }
        };
        app = FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId("stub-gems")
                .setCredentials(GoogleCredentials.create(new AccessToken("test-transport-only", new Date(Long.MAX_VALUE))))
                .setHttpTransport(transport).build(), "rsa-test-" + UUID.randomUUID());
    }
    @AfterEach void cleanup() { if (app != null) { app.delete(); } }
    String token(String audience, String issuer, long expires, KeyPair signer) throws Exception {
        var header = new JsonWebSignature.Header().setAlgorithm("RS256").setKeyId("test-key");
        var payload = new JsonWebToken.Payload().setAudience(audience).setIssuer(issuer).setSubject("uid")
                .setIssuedAtTimeSeconds(now - 60).setExpirationTimeSeconds(expires);
        payload.set("auth_time", now - 60); payload.set("email", "person@example.test"); payload.set("email_verified", true);
        payload.set("firebase", Map.of("sign_in_provider", "google.com"));
        return JsonWebSignature.signUsingRsaSha256(signer.getPrivate(), GsonFactory.getDefaultInstance(), header, payload);
    }
    @Test void rsaAndRevocationLookupAreBothPerformed() throws Exception {
        var adapter = new FirebaseAdminAdapter(app, "stub-gems", true, false);

        var identity = adapter.verify(token("stub-gems", "https://securetoken.google.com/stub-gems", now + 3600, pair));

        assertThat(identity.uid()).isEqualTo("uid"); assertThat(identity.signInProvider()).isEqualTo("google.com");
        assertThat(lookups.get()).isEqualTo(1);
    }
    @Test void wrongAudienceIssuerExpiredAndForgedSignatureAre401() throws Exception {
        var adapter = new FirebaseAdminAdapter(app, "stub-gems", true, false);
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        for (String value : java.util.List.of(token("other-gems", "https://securetoken.google.com/stub-gems", now + 3600, pair),
                token("stub-gems", "https://securetoken.google.com/other-gems", now + 3600, pair),
                token("stub-gems", "https://securetoken.google.com/stub-gems", now - 3600, pair),
                token("stub-gems", "https://securetoken.google.com/stub-gems", now + 3600, generator.generateKeyPair()))) {
            assertThatThrownBy(() -> adapter.verify(value)).isInstanceOf(FirebaseAuthException.class)
                    .extracting("reason").isEqualTo(FirebaseAuthException.Reason.INVALID_TOKEN);
        }
    }
    @Test void disabledAndRevokedIdentityAre401() throws Exception {
        var adapter = new FirebaseAdminAdapter(app, "stub-gems", true, false);
        String value = token("stub-gems", "https://securetoken.google.com/stub-gems", now + 3600, pair);
        disabled = true;
        assertThatThrownBy(() -> adapter.verify(value)).isInstanceOf(FirebaseAuthException.class).extracting("reason").isEqualTo(FirebaseAuthException.Reason.INVALID_TOKEN);
        disabled = false; validSince = now;
        assertThatThrownBy(() -> adapter.verify(value)).isInstanceOf(FirebaseAuthException.class).extracting("reason").isEqualTo(FirebaseAuthException.Reason.INVALID_TOKEN);
    }
    @Test void certificateProviderOutageIsSanitized503() throws Exception {
        outage = true; var adapter = new FirebaseAdminAdapter(app, "stub-gems", true, false);
        String value = token("stub-gems", "https://securetoken.google.com/stub-gems", now + 3600, pair);

        assertThatThrownBy(() -> adapter.verify(value)).isInstanceOf(FirebaseAuthException.class)
                .hasMessage("PROVIDER_UNAVAILABLE").hasNoCause();
    }
    @Test void explicitRealCheckRevokedFalseStillChecksSignatureWithoutLookup() throws Exception {
        var adapter = new FirebaseAdminAdapter(app, "stub-gems", false, false);

        assertThat(adapter.verify(token("stub-gems", "https://securetoken.google.com/stub-gems", now + 3600, pair)).uid()).isEqualTo("uid");
        assertThat(lookups.get()).isZero();
    }
    @Test void cachedCertificateDoesNotMaskRevocationLookupOutage() throws Exception {
        var adapter = new FirebaseAdminAdapter(app, "stub-gems", true, false);
        String signed = token("stub-gems", "https://securetoken.google.com/stub-gems", now + 3600, pair);
        assertThat(adapter.verify(signed).uid()).isEqualTo("uid");
        lookupOutage = true;
        assertThatThrownBy(() -> adapter.verify(signed)).isInstanceOf(FirebaseAuthException.class)
                .hasMessage("PROVIDER_UNAVAILABLE").hasNoCause();
        assertThat(lookups.get()).isGreaterThan(1);
    }
}
