package clyvr.backend.config;

import java.util.Map;
import java.util.Objects;

public final class BackendConfiguration {

    private final int port;
    private final String firebaseProjectId;
    private final String databaseUrl;
    private final String databaseUser;
    private final String databasePassword;
    private final boolean developmentIdentityEnabled;
    private final String bindAddress;
    private final Map<String, String> environment;

    private BackendConfiguration(
            int port,
            String firebaseProjectId,
            String databaseUrl,
            String databaseUser,
            String databasePassword,
            boolean developmentIdentityEnabled,
            String bindAddress,
            Map<String, String> environment) {
        this.port = port;
        this.firebaseProjectId = firebaseProjectId;
        this.databaseUrl = databaseUrl;
        this.databaseUser = databaseUser;
        this.databasePassword = databasePassword;
        this.developmentIdentityEnabled = developmentIdentityEnabled;
        this.bindAddress = bindAddress;
        this.environment = Map.copyOf(environment);
    }

    public static BackendConfiguration fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        int port = parsePort(environment.get("BACKEND_PORT"));
        String firebaseProjectId = optionalValue(environment.get("FIREBASE_PROJECT_ID"));
        boolean developmentIdentityEnabled = parseBoolean(
                environment.get("BACKEND_DEVELOPMENT_IDENTITY_ENABLED"));
        String bindAddress = optionalValue(environment.get("BACKEND_BIND_ADDRESS"));
        if (bindAddress == null) {
            bindAddress = "127.0.0.1";
        }
        if (developmentIdentityEnabled && !isLoopbackAddress(bindAddress)) {
            throw new IllegalStateException(
                    "Development identity requires BACKEND_BIND_ADDRESS to be a loopback address.");
        }

        String databaseUrl = optionalValue(environment.get("SUPABASE_DB_URL"));
        String databaseUser = optionalValue(environment.get("SUPABASE_DB_USER"));
        String databasePassword = optionalValue(environment.get("SUPABASE_DB_PASSWORD"));
        int databaseValues = countPresent(databaseUrl, databaseUser, databasePassword);
        if (databaseValues != 0 && databaseValues != 3) {
            throw new IllegalStateException(
                    "Set SUPABASE_DB_URL, SUPABASE_DB_USER, and SUPABASE_DB_PASSWORD together.");
        }
        if (databaseUrl != null && !databaseUrl.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("SUPABASE_DB_URL must be a PostgreSQL JDBC URL.");
        }

        return new BackendConfiguration(
                port, firebaseProjectId, databaseUrl, databaseUser, databasePassword,
                developmentIdentityEnabled, bindAddress, environment);
    }

    public int port() {
        return port;
    }

    public String firebaseProjectId() {
        return firebaseProjectId;
    }

    public boolean firebaseConfigured() {
        return firebaseProjectId != null;
    }

    public boolean databaseConfigured() {
        return databaseUrl != null;
    }

    public boolean developmentIdentityEnabled() {
        return developmentIdentityEnabled;
    }

    public String bindAddress() {
        return bindAddress;
    }

    public Map<String, String> environment() {
        return environment;
    }

    public String databaseUrl() {
        return databaseUrl;
    }

    public String databaseUser() {
        return databaseUser;
    }

    public String databasePassword() {
        return databasePassword;
    }

    @Override
    public String toString() {
        return "BackendConfiguration[port=" + port
                + ", firebaseConfigured=" + firebaseConfigured()
                + ", databaseConfigured=" + databaseConfigured()
                + ", developmentIdentityEnabled=" + developmentIdentityEnabled + "]";
    }

    private static int parsePort(String configuredPort) {
        if (configuredPort == null || configuredPort.isBlank()) {
            return 8080;
        }
        try {
            int port = Integer.parseInt(configuredPort);
            if (port < 1 || port > 65535) {
                throw new IllegalStateException("BACKEND_PORT must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("BACKEND_PORT must be a number.", exception);
        }
    }

    private static String optionalValue(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean parseBoolean(String value) {
        if (value == null || value.isBlank() || "false".equalsIgnoreCase(value)) {
            return false;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        throw new IllegalStateException(
                "BACKEND_DEVELOPMENT_IDENTITY_ENABLED must be true or false.");
    }

    private static boolean isLoopbackAddress(String address) {
        return "localhost".equalsIgnoreCase(address)
                || "127.0.0.1".equals(address)
                || "::1".equals(address)
                || "[::1]".equals(address);
    }

    private static int countPresent(String first, String second, String third) {
        return (first == null ? 0 : 1)
                + (second == null ? 0 : 1)
                + (third == null ? 0 : 1);
    }
}
