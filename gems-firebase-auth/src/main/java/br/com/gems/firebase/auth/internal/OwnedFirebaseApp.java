package br.com.gems.firebase.auth.internal;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import br.com.gems.firebase.auth.FirebaseAuthProperties;
import java.io.IOException;
import java.util.Date;

/** Dono interno: somente a app criada por esta instância é destruída. */
public final class OwnedFirebaseApp implements AutoCloseable {
    private final FirebaseApp app;
    public OwnedFirebaseApp(FirebaseAuthProperties properties) {
        try {
            GoogleCredentials credentials = properties.emulator().enabled()
                    ? GoogleCredentials.create(new AccessToken("emulator-only", new Date(Long.MAX_VALUE)))
                    : GoogleCredentials.getApplicationDefault();
            app = FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(properties.projectId())
                    .setCredentials(credentials).build(), properties.appName());
        } catch (IOException | IllegalStateException failure) {
            throw new IllegalArgumentException("Inicialização Firebase recusada");
        }
    }
    /** Acesso exclusivo da infraestrutura interna. */
    public FirebaseApp app() { return app; }
    @Override public void close() { app.delete(); }
}
