package br.com.gems.firebase.auth;

import br.com.gems.security.authorization.AuthorizationContextAware;
import br.com.gems.security.authorization.JwtAuthorizationContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

class FirebaseAuthenticationFilterTest {
    static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    static final VerifiedIdentity IDENTITY = new VerifiedIdentity("demo-gems", "https://securetoken.google.com/demo-gems", "uid", "email@example.test", true, "google.com", "Person", NOW);
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final AuthorizationResolver.Resolution resolution = new AuthorizationResolver.Resolution(
            new FirebasePrincipal(UUID.randomUUID(), "uid", "Person", "email@example.test"),
            new JwtAuthorizationContext(Set.of(), Set.of("CONSULTAR_CONTA"), Set.of("READER"), Set.of("CONSULTAR_DEMO"), Optional.of("alpha")));

    @Test void installsCanonicalActionContextAndClearsBeforeAndAfter() throws Exception {
        var filter = new FirebaseAuthenticationFilter(token -> IDENTITY, (identity, alias, now) -> {
            assertThat(now).isEqualTo(NOW); assertThat(alias).contains("alpha"); return resolution;
        }, clock, "X-Tenant-Alias");
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer stub"); request.addHeader("X-Tenant-Alias", "alpha");
        SecurityContextHolder.getContext().setAuthentication(new FirebaseAuthenticationToken(resolution));

        filter.doFilter(request, new MockHttpServletResponse(), (req, response) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isInstanceOf(AuthorizationContextAware.class);
            assertThat(((AuthorizationContextAware) authentication).authorizationContext()).isEqualTo(resolution.context());
            assertThat(authentication.getAuthorities()).extracting("authority").containsExactlyInAnyOrder("ROLE_CONSULTAR_CONTA", "ROLE_CONSULTAR_DEMO");
            assertThat(authentication.getCredentials()).isNull();
        });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @ParameterizedTest @EnumSource(FirebaseAuthException.Reason.class)
    void failuresKeepDistinctSanitizedStatusAndNoContext(FirebaseAuthException.Reason reason) throws Exception {
        var filter = new FirebaseAuthenticationFilter(token -> { throw new FirebaseAuthException(reason); }, (id, alias, now) -> resolution, clock, "X-Tenant-Alias");
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer secret-token");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("denied chain"); });

        assertThat(response.getStatus()).isEqualTo(reason.status());
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"" + reason.name() + "\"}");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void duplicateBearerIsRejectedBeforeVerification() throws Exception {
        var filter = new FirebaseAuthenticationFilter(token -> { throw new AssertionError("ambiguous header"); }, (id, alias, now) -> resolution, clock, "X-Tenant-Alias");
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer first"); request.addHeader("Authorization", "Bearer second");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("chain"); });
        assertThat(response.getStatus()).isEqualTo(401);
    }
    @Test void duplicateAliasFailsClosed() throws Exception {
        var filter = new FirebaseAuthenticationFilter(token -> IDENTITY, (id, alias, now) -> resolution, clock, "X-Tenant-Alias");
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer stub");
        request.addHeader("X-Tenant-Alias", "alpha"); request.addHeader("X-Tenant-Alias", "beta");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("chain"); });
        assertThat(response.getStatus()).isEqualTo(403);
    }
    @Test void anonymousRequestCannotInheritPreviousAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new FirebaseAuthenticationToken(resolution));
        var filter = new FirebaseAuthenticationFilter(token -> IDENTITY, (id, alias, now) -> resolution, clock, "X-Tenant-Alias");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void downstreamFailureStillClearsAuthentication() {
        var filter = new FirebaseAuthenticationFilter(token -> IDENTITY, (id, alias, now) -> resolution, clock, "X-Tenant-Alias");
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer stub");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { throw new IllegalStateException("business"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void refusalAuditNeverContainsTokenHeaderAliasOrProviderPayload() throws Exception {
        var events = new java.util.ArrayList<SecurityEventSink.Event>();
        var filter = new FirebaseAuthenticationFilter(token -> { throw new FirebaseAuthException(FirebaseAuthException.Reason.INVALID_TOKEN); },
                (id, alias, now) -> resolution, clock, "X-Tenant-Alias", events::add);
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer sensitive-token");
        request.addHeader("X-Tenant-Alias", "sensitive-alias"); var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("denied"); });
        assertThat(events).hasSize(1);
        var event = events.getFirst(); assertThat(event.type()).isEqualTo("ACCESS_REFUSED_INVALID_TOKEN");
        assertThat(event.correlationId().toString()).isEqualTo(response.getHeader("X-Correlation-ID"));
        assertThat(event.toString()).doesNotContain("sensitive-token", "sensitive-alias", "Bearer", "password", "payload");
    }
    @Test void unexpectedProviderFailureIsSanitizedAndStillAudited() throws Exception {
        var events = new java.util.ArrayList<SecurityEventSink.Event>();
        var filter = new FirebaseAuthenticationFilter(token -> { throw new IllegalStateException("sensitive-provider-payload"); },
                (id, alias, now) -> resolution, clock, "X-Tenant-Alias", events::add);
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer sensitive-token");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("denied"); });
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"PROVIDER_UNAVAILABLE\"}");
        assertThat(events.getFirst().toString()).doesNotContain("sensitive-provider-payload", "sensitive-token");
    }
    @Test void defaultPublisherWritesStructuredRefusalFieldsAndNoThrowable() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LoggingSecurityEventPublisher.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            var filter = new FirebaseAuthenticationFilter(token -> { throw new FirebaseAuthException(FirebaseAuthException.Reason.INVALID_TOKEN); },
                    (id, alias, now) -> resolution, clock, "X-Tenant-Alias");
            var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer sensitive-token");
            var response = new MockHttpServletResponse(); filter.doFilter(request, response, (req, res) -> { throw new AssertionError("denied"); });
            assertThat(appender.list).hasSize(1); var log = appender.list.getFirst();
            assertThat(log.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR); assertThat(log.getThrowableProxy()).isNull();
            assertThat(log.getKeyValuePairs()).extracting(pair -> pair.key).contains("organizacao", "causa", "modulo", "correlacao", "fonteAlias", "pontoRecusa");
            assertThat(log.getKeyValuePairs().stream().filter(pair -> pair.key.equals("correlacao")).findFirst().orElseThrow().value.toString())
                    .isEqualTo(response.getHeader("X-Correlation-ID"));
            assertThat(log.getFormattedMessage()).doesNotContain("sensitive-token", "Bearer", "payload");
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
}
