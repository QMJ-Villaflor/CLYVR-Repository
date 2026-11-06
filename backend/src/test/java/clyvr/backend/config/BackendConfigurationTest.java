package clyvr.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BackendConfigurationTest {

    @Test
    void usesSafeDefaultsWhenOptionalIntegrationsAreNotConfigured() {
        BackendConfiguration configuration = BackendConfiguration.fromEnvironment(Map.of());

        assertEquals(8080, configuration.port());
        assertFalse(configuration.firebaseConfigured());
        assertFalse(configuration.databaseConfigured());
    }

    @Test
    void acceptsCompleteBackendConfigurationWithoutIncludingSecretsInItsStringForm() {
        String databasePassword = "synthetic-password-marker";
        BackendConfiguration configuration = BackendConfiguration.fromEnvironment(Map.of(
                "BACKEND_PORT", "9090",
                "FIREBASE_PROJECT_ID", "clyvr-test-project",
                "SUPABASE_DB_URL", "jdbc:postgresql://db.invalid:5432/test",
                "SUPABASE_DB_USER", "backend-test-user",
                "SUPABASE_DB_PASSWORD", databasePassword));

        assertEquals(9090, configuration.port());
        assertTrue(configuration.firebaseConfigured());
        assertTrue(configuration.databaseConfigured());
        assertFalse(configuration.toString().contains(databasePassword));
        assertFalse(configuration.toString().contains("db.invalid"));
    }

    @Test
    void rejectsPartialDatabaseConfigurationWithoutEchoingValues() {
        String passwordMarker = "must-not-appear";
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> BackendConfiguration.fromEnvironment(Map.of(
                        "SUPABASE_DB_PASSWORD", passwordMarker)));

        assertFalse(exception.getMessage().contains(passwordMarker));
    }

    @Test
    void rejectsNonPostgresDatabaseUrlWithoutEchoingIt() {
        String urlMarker = "mysql://private-host/private-db";
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> BackendConfiguration.fromEnvironment(Map.of(
                        "SUPABASE_DB_URL", urlMarker,
                        "SUPABASE_DB_USER", "test-user",
                        "SUPABASE_DB_PASSWORD", "test-password")));

        assertFalse(exception.getMessage().contains(urlMarker));
    }

    @Test
    void rejectsInvalidPort() {
        assertThrows(IllegalStateException.class,
                () -> BackendConfiguration.fromEnvironment(Map.of("BACKEND_PORT", "70000")));
    }

    @Test
    void enablesTemporaryIdentityOnlyWhenExplicitlyConfiguredOnLoopback() {
        BackendConfiguration configuration = BackendConfiguration.fromEnvironment(Map.of(
                "BACKEND_DEVELOPMENT_IDENTITY_ENABLED", "true",
                "BACKEND_BIND_ADDRESS", "127.0.0.1"));

        assertTrue(configuration.developmentIdentityEnabled());
        assertEquals("127.0.0.1", configuration.bindAddress());
    }

    @Test
    void refusesDevelopmentIdentityOnNetworkInterface() {
        assertThrows(IllegalStateException.class,
                () -> BackendConfiguration.fromEnvironment(Map.of(
                        "BACKEND_DEVELOPMENT_IDENTITY_ENABLED", "true",
                        "BACKEND_BIND_ADDRESS", "0.0.0.0")));
    }
}
