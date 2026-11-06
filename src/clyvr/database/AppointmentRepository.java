package clyvr.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AppointmentRepository {

    static final String PENDING_CONSULTATIONS_SQL = """
        SELECT appointment_id, patient_id, doctor_id, appointment_date,
               slot_number, reason
        FROM public.appointments
        WHERE appointment_type = 'CONSULTATION'
          AND status = 'PENDING_RECEPTIONIST'
        ORDER BY appointment_date, slot_number, appointment_id
        """;

    static final String INSERT_CONSULTATION_SQL = """
        INSERT INTO public.appointments
            (patient_id, doctor_id, appointment_type, appointment_date,
             slot_number, status, reason)
        VALUES (?, ?, 'CONSULTATION', ?, ?, 'PENDING_RECEPTIONIST', ?)
        RETURNING appointment_id
        """;

    private final ConnectionProvider connectionProvider;
    private final DemoIdMapping idMapping;

    public AppointmentRepository(ConnectionProvider connectionProvider, DemoIdMapping idMapping) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
        this.idMapping = Objects.requireNonNull(idMapping);
    }

    public static AppointmentRepository usingConfiguredDatabase() {
        return new AppointmentRepository(Database::getConnection, DemoIdMapping.fromSystemEnvironment());
    }

    public long insertConsultationRequest(
            String demoPatientId,
            String demoDoctorId,
            LocalDate appointmentDate,
            int slotNumber,
            String reason) throws SQLException {
        Objects.requireNonNull(appointmentDate, "appointmentDate");
        Objects.requireNonNull(reason, "reason");
        if (slotNumber < 0 || slotNumber > 7) {
            throw new IllegalArgumentException("Slot number must be between 0 and 7.");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("A consultation reason is required.");
        }

        long patientId = idMapping.patientId(demoPatientId);
        long doctorId = idMapping.doctorId(demoDoctorId);

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_CONSULTATION_SQL)) {
            statement.setLong(1, patientId);
            statement.setLong(2, doctorId);
            statement.setObject(3, appointmentDate);
            statement.setInt(4, slotNumber);
            statement.setString(5, reason);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Database did not return the generated appointment ID.");
                }
                long appointmentId = result.getLong("appointment_id");
                if (result.wasNull()) {
                    throw new SQLException("Database returned a null appointment ID.");
                }
                return appointmentId;
            }
        }
    }

    public List<PendingConsultationRequest> findPendingConsultationRequests() throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(PENDING_CONSULTATIONS_SQL)) {
            try (ResultSet result = statement.executeQuery()) {
                List<PendingConsultationRequest> requests = new ArrayList<>();
                while (result.next()) {
                    long appointmentId = requiredId(result, "appointment_id");
                    long patientId = requiredId(result, "patient_id");
                    long doctorId = requiredId(result, "doctor_id");
                    LocalDate appointmentDate = result.getObject("appointment_date", LocalDate.class);
                    if (appointmentDate == null) {
                        throw new SQLException("Pending consultation has no appointment date.");
                    }
                    int slotNumber = result.getInt("slot_number");
                    if (result.wasNull()) {
                        throw new SQLException("Pending consultation has no slot number.");
                    }

                    try {
                        requests.add(new PendingConsultationRequest(
                            appointmentId,
                            patientId,
                            doctorId,
                            appointmentDate,
                            slotNumber,
                            result.getString("reason")
                        ));
                    } catch (IllegalArgumentException | NullPointerException exception) {
                        throw new SQLException("Database returned an invalid pending consultation.", exception);
                    }
                }
                return List.copyOf(requests);
            }
        }
    }

    private static long requiredId(ResultSet result, String column) throws SQLException {
        long id = result.getLong(column);
        if (result.wasNull() || id <= 0) {
            throw new SQLException("Pending consultation has an invalid " + column + ".");
        }
        return id;
    }

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }
}
