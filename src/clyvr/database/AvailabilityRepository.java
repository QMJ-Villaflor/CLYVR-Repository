package clyvr.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AvailabilityRepository {

    static final String APPOINTMENTS_SQL = """
        SELECT patient_id, doctor_id, slot_number
        FROM public.appointments
        WHERE appointment_date = ?
          AND (doctor_id = ? OR patient_id = ?)
          AND status IN ('PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED')
        """;

    static final String APPROVED_TIME_OFF_SQL = """
        SELECT slot_number
        FROM public.time_off_requests
        WHERE doctor_id = ? AND request_date = ? AND status = 'APPROVED'
        """;

    static final String RESCHEDULE_HOLDS_SQL = """
        SELECT a.patient_id, a.doctor_id, r.proposed_slot, r.status
        FROM public.appointment_change_requests r
        JOIN public.appointments a ON a.appointment_id = r.appointment_id
        WHERE r.kind = 'RESCHEDULE'
          AND r.proposed_date = ?
          AND r.proposed_slot IS NOT NULL
          AND (a.doctor_id = ? OR a.patient_id = ?)
        """;

    private final AppointmentRepository.ConnectionProvider connectionProvider;
    private final DemoIdMapping idMapping;

    public AvailabilityRepository(
            AppointmentRepository.ConnectionProvider connectionProvider,
            DemoIdMapping idMapping) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
        this.idMapping = Objects.requireNonNull(idMapping);
    }

    public static AvailabilityRepository usingConfiguredDatabase() {
        return new AvailabilityRepository(Database::getConnection, DemoIdMapping.fromSystemEnvironment());
    }

    public DoctorAvailability findForDemoPatient(
            String demoPatientKey,
            long databaseDoctorId,
            LocalDate day) throws SQLException {
        Objects.requireNonNull(day, "day");
        if (databaseDoctorId <= 0) {
            throw new IllegalArgumentException("Database doctor ID must be positive.");
        }
        long databasePatientId = idMapping.patientId(demoPatientKey);
        AvailabilitySlot.State[] states = new AvailabilitySlot.State[8];
        java.util.Arrays.fill(states, AvailabilitySlot.State.AVAILABLE);

        try (Connection connection = connectionProvider.getConnection()) {
            readAppointments(connection, databasePatientId, databaseDoctorId, day, states);
            readApprovedTimeOff(connection, databaseDoctorId, day, states);
            readPendingRescheduleHolds(connection, databasePatientId, databaseDoctorId, day, states);
        }

        List<AvailabilitySlot> slots = new ArrayList<>(states.length);
        for (int slot = 0; slot < states.length; slot++) {
            slots.add(new AvailabilitySlot(slot, states[slot]));
        }
        return new DoctorAvailability(databaseDoctorId, slots);
    }

    private static void readAppointments(
            Connection connection,
            long patientId,
            long doctorId,
            LocalDate day,
            AvailabilitySlot.State[] states) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(APPOINTMENTS_SQL)) {
            statement.setObject(1, day);
            statement.setLong(2, doctorId);
            statement.setLong(3, patientId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long rowPatientId = requiredId(result, "patient_id");
                    long rowDoctorId = requiredId(result, "doctor_id");
                    int slot = requiredSlot(result);
                    AvailabilitySlot.State state = rowPatientId == patientId
                        ? AvailabilitySlot.State.PATIENT_CONFLICT
                        : AvailabilitySlot.State.APPOINTMENT;
                    if (rowDoctorId == doctorId || rowPatientId == patientId) {
                        states[slot] = state;
                    } else {
                        throw new SQLException("Availability query returned an out-of-scope appointment.");
                    }
                }
            }
        }
    }

    private static void readApprovedTimeOff(
            Connection connection,
            long doctorId,
            LocalDate day,
            AvailabilitySlot.State[] states) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(APPROVED_TIME_OFF_SQL)) {
            statement.setLong(1, doctorId);
            statement.setObject(2, day);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int slot = requiredSlot(result);
                    if (states[slot] == AvailabilitySlot.State.AVAILABLE) {
                        states[slot] = AvailabilitySlot.State.APPROVED_TIME_OFF;
                    }
                }
            }
        }
    }

    private static void readPendingRescheduleHolds(
            Connection connection,
            long patientId,
            long doctorId,
            LocalDate day,
            AvailabilitySlot.State[] states) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RESCHEDULE_HOLDS_SQL)) {
            statement.setObject(1, day);
            statement.setLong(2, doctorId);
            statement.setLong(3, patientId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String status = result.getString("status");
                    if (!"PENDING".equalsIgnoreCase(status)) {
                        continue;
                    }
                    long rowPatientId = requiredId(result, "patient_id");
                    long rowDoctorId = requiredId(result, "doctor_id");
                    int slot = requiredSlot(result, "proposed_slot");
                    if (rowPatientId == patientId) {
                        states[slot] = AvailabilitySlot.State.PATIENT_CONFLICT;
                    } else if (rowDoctorId == doctorId
                            && states[slot] == AvailabilitySlot.State.AVAILABLE) {
                        states[slot] = AvailabilitySlot.State.RESCHEDULE_HOLD;
                    } else if (rowDoctorId != doctorId) {
                        throw new SQLException("Availability query returned an out-of-scope reschedule.");
                    }
                }
            }
        }
    }

    private static long requiredId(ResultSet result, String column) throws SQLException {
        long id = result.getLong(column);
        if (result.wasNull() || id <= 0) {
            throw new SQLException("Availability query returned an invalid " + column + ".");
        }
        return id;
    }

    private static int requiredSlot(ResultSet result) throws SQLException {
        return requiredSlot(result, "slot_number");
    }

    private static int requiredSlot(ResultSet result, String column) throws SQLException {
        int slot = result.getInt(column);
        if (result.wasNull() || slot < 0 || slot > 7) {
            throw new SQLException("Availability query returned an invalid slot number.");
        }
        return slot;
    }
}
