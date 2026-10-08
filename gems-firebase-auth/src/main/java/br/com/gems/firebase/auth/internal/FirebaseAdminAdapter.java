package br.com.gems.firebase.auth.internal;

import br.com.gems.firebase.auth.FirebaseAdminGateway;
import br.com.gems.firebase.auth.IdTokenVerifier;
import br.com.gems.firebase.auth.VerifiedIdentity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import java.time.Instant;
import java.util.Map;

/** Adapter interno; tipos Admin não pertencem à superfície de integração. */
public final class FirebaseAdminAdapter {
    private final FirebaseAuth auth;
    private final String projectId;
    private final boolean checkRevoked;
    private final boolean emulator;
    public FirebaseAdminAdapter(FirebaseApp app, String projectId, boolean checkRevoked, boolean emulator) {
        this.auth = FirebaseAuth.getInstance(app); this.projectId = projectId;
        this.checkRevoked = checkRevoked; this.emulator = emulator;
    }
    public VerifiedIdentity verify(String token) {
        try {
            // Admin emulator sempre consulta revogação/disabled. Não prova checkRevoked=false real.
            var verified = auth.verifyIdToken(token, checkRevoked || emulator);
            var claims = verified.getClaims();
            if (!projectId.equals(claims.get("aud"))
                    || !("https://securetoken.google.com/" + projectId).equals(verified.getIssuer())) {
                throw new br.com.gems.firebase.auth.FirebaseAuthException(
                        br.com.gems.firebase.auth.FirebaseAuthException.Reason.INVALID_TOKEN);
            }
            Object firebase = claims.get("firebase");
            String provider = firebase instanceof Map<?, ?> values && values.get("sign_in_provider") instanceof String value
                    ? value : "";
            Object authTime = claims.get("auth_time");
            if (!(authTime instanceof Number number)) {
                throw new br.com.gems.firebase.auth.FirebaseAuthException(
                        br.com.gems.firebase.auth.FirebaseAuthException.Reason.INVALID_TOKEN);
            }
            return new VerifiedIdentity(projectId, verified.getIssuer(), verified.getUid(), verified.getEmail(),
                    verified.isEmailVerified(), provider, verified.getName(), Instant.ofEpochSecond(number.longValue()));
        } catch (com.google.firebase.auth.FirebaseAuthException failure) { throw mapped(failure, true); }
        catch (IllegalArgumentException failure) {
            throw new br.com.gems.firebase.auth.FirebaseAuthException(
                    br.com.gems.firebase.auth.FirebaseAuthException.Reason.INVALID_TOKEN);
        }
    }
    public FirebaseAdminGateway.User getUser(String uid) {
        try { return snapshot(auth.getUser(uid)); }
        catch (com.google.firebase.auth.FirebaseAuthException failure) { throw mapped(failure, false); }
    }
    public FirebaseAdminGateway.User createUser(String uid, String email, String displayName) {
        try { return snapshot(auth.createUser(new UserRecord.CreateRequest().setUid(uid)
                .setEmail(email).setDisplayName(displayName))); }
        catch (com.google.firebase.auth.FirebaseAuthException failure) {
            if (failure.getAuthErrorCode() == AuthErrorCode.UID_ALREADY_EXISTS) {
                FirebaseAdminGateway.User existing = getUser(uid);
                if (java.util.Objects.equals(existing.email(), email)) { return existing; }
            }
            throw mapped(failure, false);
        }
    }
    public void setDisabled(String uid, boolean disabled) {
        try { auth.updateUser(new UserRecord.UpdateRequest(uid).setDisabled(disabled)); }
        catch (com.google.firebase.auth.FirebaseAuthException failure) { throw mapped(failure, false); }
    }
    public void revokeRefreshTokens(String uid) {
        try { auth.revokeRefreshTokens(uid); }
        catch (com.google.firebase.auth.FirebaseAuthException failure) { throw mapped(failure, false); }
    }
    public String passwordResetLink(String email) {
        try { return auth.generatePasswordResetLink(email); }
        catch (com.google.firebase.auth.FirebaseAuthException failure) { throw mapped(failure, false); }
    }
    private static FirebaseAdminGateway.User snapshot(UserRecord user) {
        return new FirebaseAdminGateway.User(user.getUid(), user.getEmail(), user.getDisplayName(), user.isDisabled(), user.isEmailVerified());
    }
    private static br.com.gems.firebase.auth.FirebaseAuthException mapped(
            com.google.firebase.auth.FirebaseAuthException failure, boolean token) {
        var code = failure.getAuthErrorCode();
        boolean invalid = code == AuthErrorCode.INVALID_ID_TOKEN || code == AuthErrorCode.EXPIRED_ID_TOKEN
                || code == AuthErrorCode.REVOKED_ID_TOKEN || code == AuthErrorCode.USER_DISABLED
                || code == AuthErrorCode.USER_NOT_FOUND;
        return new br.com.gems.firebase.auth.FirebaseAuthException(token && invalid
                ? br.com.gems.firebase.auth.FirebaseAuthException.Reason.INVALID_TOKEN
                : br.com.gems.firebase.auth.FirebaseAuthException.Reason.PROVIDER_UNAVAILABLE);
    }
}
