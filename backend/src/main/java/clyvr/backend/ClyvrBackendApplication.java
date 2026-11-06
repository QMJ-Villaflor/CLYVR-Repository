package clyvr.backend;

import clyvr.backend.config.BackendConfiguration;
import clyvr.backend.database.ClyvrDataRepository;
import clyvr.backend.database.JdbcClyvrDataRepository;
import clyvr.backend.database.SupabaseDatabaseAccess;
import clyvr.backend.identity.DemoIdentityMappings;
import clyvr.backend.identity.FirebaseAdminTokenVerifier;
import clyvr.backend.identity.FirebaseTokenVerifier;
import clyvr.backend.identity.TokenVerifierUnavailableException;
import clyvr.backend.security.DatabaseSessionService;
import clyvr.backend.security.FirebaseScrypt;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.IOException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ClyvrBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClyvrBackendApplication.class, args);
    }

    @Bean
    BackendConfiguration backendConfiguration() {
        return BackendConfiguration.fromEnvironment(System.getenv());
    }

    @Bean
    SupabaseDatabaseAccess supabaseDatabaseAccess(BackendConfiguration configuration) {
        return new SupabaseDatabaseAccess(configuration);
    }

    @Bean
    DemoIdentityMappings demoIdentityMappings(BackendConfiguration configuration) {
        return new DemoIdentityMappings(configuration.environment());
    }

    @Bean
    ClyvrDataRepository clyvrDataRepository(SupabaseDatabaseAccess databaseAccess) {
        return new JdbcClyvrDataRepository(databaseAccess::openConnection);
    }

    @Bean
    DatabaseSessionService databaseSessionService(
            BackendConfiguration configuration, ClyvrDataRepository repository) {
        String signerKey = configuration.environment().get("CLYVR_HASH_SIGNER_KEY");
        String saltSeparator = configuration.environment().get("CLYVR_HASH_SALT_SEPARATOR");
        FirebaseScrypt hasher = signerKey == null || signerKey.isBlank()
                || saltSeparator == null || saltSeparator.isBlank()
                ? null : new FirebaseScrypt(signerKey, saltSeparator);
        return new DatabaseSessionService(repository, hasher);
    }

    @Bean
    FirebaseTokenVerifier firebaseTokenVerifier(BackendConfiguration configuration) {
        if (!configuration.firebaseConfigured()) {
            return token -> {
                throw new TokenVerifierUnavailableException();
            };
        }

        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(configuration.firebaseProjectId())
                    .build();
            FirebaseApp firebaseApp = FirebaseApp.initializeApp(options, "clyvr-backend");
            return new FirebaseAdminTokenVerifier(FirebaseAuth.getInstance(firebaseApp));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                    "Firebase Admin credentials are unavailable or invalid.", exception);
        }
    }
}
