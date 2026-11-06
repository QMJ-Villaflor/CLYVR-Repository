package clyvr.backend.database;

import clyvr.backend.config.BackendConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

public final class SupabaseDatabaseAccess {

    private final BackendConfiguration configuration;

    public SupabaseDatabaseAccess(BackendConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public Connection openConnection() throws SQLException {
        if (!configuration.databaseConfigured()) {
            throw new IllegalStateException("Supabase database access is not configured.");
        }
        Properties credentials = new Properties();
        credentials.setProperty("user", configuration.databaseUser());
        credentials.setProperty("password", configuration.databasePassword());
        return DriverManager.getConnection(configuration.databaseUrl(), credentials);
    }
}
