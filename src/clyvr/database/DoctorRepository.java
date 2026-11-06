package clyvr.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

public final class DoctorRepository {

    static final String DIRECTORY_SQL_PREFIX = """
        SELECT d.doctor_id, d.user_id, u.first_name, u.last_name, d.specialty, u.is_active
        FROM public.doctors d
        LEFT JOIN public.users u ON u.user_id = d.user_id
        WHERE d.doctor_id IN (
        """;
    static final String DIRECTORY_SQL_SUFFIX = """
        )
        ORDER BY d.doctor_id
        """;

    private final AppointmentRepository.ConnectionProvider connectionProvider;
    private final DemoIdMapping idMapping;

    public DoctorRepository(
            AppointmentRepository.ConnectionProvider connectionProvider,
            DemoIdMapping idMapping) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
        this.idMapping = Objects.requireNonNull(idMapping);
    }

    public static DoctorRepository usingConfiguredDatabase() {
        return new DoctorRepository(Database::getConnection, DemoIdMapping.fromSystemEnvironment());
    }

    public List<DoctorDirectoryEntry> findMappedDoctors() throws SQLException {
        List<Long> doctorIds = idMapping.configuredDoctorIds();
        if (doctorIds.isEmpty()) {
            return List.of();
        }

        StringJoiner placeholders = new StringJoiner(", ");
        for (int i = 0; i < doctorIds.size(); i++) {
            placeholders.add("?");
        }
        String sql = DIRECTORY_SQL_PREFIX + placeholders + DIRECTORY_SQL_SUFFIX;

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < doctorIds.size(); i++) {
                statement.setLong(i + 1, doctorIds.get(i));
            }

            try (ResultSet result = statement.executeQuery()) {
                List<DoctorDirectoryEntry> doctors = new ArrayList<>();
                Set<Long> seenDoctorIds = new HashSet<>();
                Set<Long> expectedDoctorIds = new HashSet<>(doctorIds);
                while (result.next()) {
                    long doctorId = requiredId(result, "doctor_id");
                    if (!seenDoctorIds.add(doctorId)) {
                        throw new SQLException("Database returned more than one row for a mapped doctor.");
                    }

                    long userId = requiredId(result, "user_id");
                    String firstName = result.getString("first_name");
                    String lastName = result.getString("last_name");
                    String specialty = result.getString("specialty");
                    if (firstName == null || lastName == null) {
                        throw new SQLException("A mapped doctor is missing its linked user record.");
                    }
                    boolean active = result.getBoolean("is_active");
                    if (result.wasNull()) {
                        throw new SQLException("A mapped doctor is missing its linked user record.");
                    }
                    if (!active) {
                        continue;
                    }

                    try {
                        doctors.add(new DoctorDirectoryEntry(
                            doctorId, userId, firstName, lastName, specialty
                        ));
                    } catch (IllegalArgumentException | NullPointerException exception) {
                        throw new SQLException("Database returned an invalid mapped doctor.", exception);
                    }
                }
                if (!seenDoctorIds.equals(expectedDoctorIds)) {
                    throw new SQLException("One or more configured database doctor IDs were not found.");
                }
                return List.copyOf(doctors);
            }
        }
    }

    private static long requiredId(ResultSet result, String column) throws SQLException {
        long id = result.getLong(column);
        if (result.wasNull() || id <= 0) {
            throw new SQLException("Database returned an invalid mapped doctor " + column + ".");
        }
        return id;
    }
}
