package clyvr.backend.database;

import clyvr.backend.identity.BackendRole;
import clyvr.backend.security.UserCredential;
import clyvr.backend.security.AccountAlreadyExistsException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

public final class JdbcClyvrDataRepository implements ClyvrDataRepository {

    static final String CREDENTIAL_SQL = """
        SELECT u.user_id, u.password_hash, u.role, u.is_active, d.doctor_id,
               u.first_name, u.last_name, u.email, u.phone
        FROM public.users u
        LEFT JOIN public.doctors d ON d.user_id = u.user_id
        WHERE lower(u.email) = lower(?) OR lower(u.username) = lower(?)
        """;
    static final String CREDENTIAL_BY_ID_SQL = CREDENTIAL_SQL.substring(
            0, CREDENTIAL_SQL.indexOf("WHERE"))
            + "WHERE u.user_id = ?";
    static final String INSERT_PATIENT_USER_SQL = """
        INSERT INTO public.users
            (username, email, password_hash, role, is_active, first_name, last_name, phone)
        VALUES (?, ?, ?, 'PATIENT', TRUE, ?, ?, ?)
        RETURNING user_id
        """;
    static final String INSERT_PATIENT_PROFILE_SQL = """
        INSERT INTO public.patient_profiles (user_id, birth_date, sex, address)
        VALUES (?, ?, ?, ?)
        """;
    static final String UPDATE_PASSWORD_HASH_SQL = """
        UPDATE public.users SET password_hash = ? WHERE user_id = ? AND is_active = TRUE
        """;

    static final String IDENTITY_SQL = """
        SELECT u.user_id, u.role, u.is_active, d.doctor_id
        FROM public.users u
        LEFT JOIN public.doctors d ON d.user_id = u.user_id
        WHERE u.user_id = ?
        """;
    static final String DOCTOR_SCHEDULE_HOLDS_SQL = """
        SELECT slot_number
        FROM public.appointment_holds
        WHERE doctor_id = ? AND appointment_date = ?
          AND status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP
        """;
    static final String DOCTOR_SQL_PREFIX = """
        SELECT d.doctor_id, d.user_id, u.first_name, u.last_name, d.specialty,
               u.is_active, u.role
        FROM public.doctors d
        JOIN public.users u ON u.user_id = d.user_id
        WHERE d.doctor_id IN (
        """;
    static final String DOCTOR_SQL_SUFFIX = ") ORDER BY d.doctor_id";
    static final String PENDING_SQL = """
        SELECT appointment_id, patient_id, doctor_id, appointment_date,
               slot_number, reason
        FROM public.appointments
        WHERE appointment_type = 'CONSULTATION'
          AND status = 'PENDING_RECEPTIONIST'
        ORDER BY appointment_date, slot_number, appointment_id
        """;
    static final String APPOINTMENTS_SQL = """
        SELECT patient_id, doctor_id, slot_number
        FROM public.appointments
        WHERE appointment_date = ?
          AND (doctor_id = ? OR patient_id = ?)
          AND status IN ('PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED')
        """;
    static final String DOCTOR_SCHEDULE_APPOINTMENTS_SQL = """
        SELECT slot_number
        FROM public.appointments
        WHERE appointment_date = ? AND doctor_id = ?
          AND status IN ('PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED')
        """;
    static final String DOCTOR_SCHEDULE_RESCHEDULE_SQL = """
        SELECT r.proposed_slot
        FROM public.appointment_change_requests r
        JOIN public.appointments a ON a.appointment_id = r.appointment_id
        WHERE r.kind = 'RESCHEDULE'
          AND r.status = 'PENDING'
          AND r.proposed_date = ?
          AND r.proposed_slot IS NOT NULL
          AND a.doctor_id = ?
        """;
    static final String TIME_OFF_SQL = """
        SELECT slot_number
        FROM public.time_off_requests
        WHERE doctor_id = ? AND request_date = ? AND status = 'APPROVED'
        """;
    static final String RESCHEDULE_SQL = """
        SELECT a.patient_id, a.doctor_id, r.proposed_slot
        FROM public.appointment_change_requests r
        JOIN public.appointments a ON a.appointment_id = r.appointment_id
        WHERE r.kind = 'RESCHEDULE'
          AND r.status = 'PENDING'
          AND r.proposed_date = ?
          AND r.proposed_slot IS NOT NULL
          AND (a.doctor_id = ? OR a.patient_id = ?)
        """;
    static final String LOCK_SQL =
            "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))";
    static final String ACTIVE_CONFLICT_SQL = """
        SELECT 1
        FROM public.appointments
        WHERE appointment_date = ?
          AND slot_number = ?
          AND status IN ('PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED')
          AND (doctor_id = ? OR patient_id = ?)
        LIMIT 1
        """;
    static final String TIME_OFF_CONFLICT_SQL = """
        SELECT 1
        FROM public.time_off_requests
        WHERE doctor_id = ? AND request_date = ? AND slot_number = ?
          AND status = 'APPROVED'
        LIMIT 1
        """;
    static final String RESCHEDULE_CONFLICT_SQL = """
        SELECT 1
        FROM public.appointment_change_requests r
        JOIN public.appointments a ON a.appointment_id = r.appointment_id
        WHERE r.kind = 'RESCHEDULE' AND r.status = 'PENDING'
          AND r.proposed_date = ? AND r.proposed_slot = ?
          AND (a.doctor_id = ? OR a.patient_id = ?)
        LIMIT 1
        """;
    static final String EXPIRE_SLOT_HOLDS_SQL = """
        UPDATE public.appointment_holds
        SET status = 'EXPIRED'
        WHERE status = 'ACTIVE' AND expires_at <= CURRENT_TIMESTAMP
          AND appointment_date = ? AND slot_number = ?
          AND (doctor_id = ? OR patient_id = ?)
        """;
    static final String EXPIRE_SLOT_HOLDS_EXCEPT_HOLD_SQL = """
        UPDATE public.appointment_holds
        SET status = 'EXPIRED'
        WHERE status = 'ACTIVE' AND expires_at <= CURRENT_TIMESTAMP
          AND appointment_date = ? AND slot_number = ?
          AND (doctor_id = ? OR patient_id = ?) AND hold_id <> ?
        """;
    static final String ACTIVE_HOLD_CONFLICT_SQL = """
        SELECT 1
        FROM public.appointment_holds
        WHERE status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP
          AND appointment_date = ? AND slot_number = ?
          AND (doctor_id = ? OR patient_id = ?)
          AND (? = 0 OR hold_id <> ?)
        LIMIT 1
        """;
    static final String INSERT_HOLD_SQL = """
        INSERT INTO public.appointment_holds
            (patient_id, doctor_id, appointment_date, slot_number, status, expires_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '5 minutes')
        RETURNING hold_id, expires_at
        """;
    static final String LOCK_PATIENT_HOLD_SQL = """
        SELECT hold_id, patient_id, doctor_id, appointment_date, slot_number,
               status, expires_at, expires_at > CURRENT_TIMESTAMP AS is_unexpired
        FROM public.appointment_holds
        WHERE hold_id = ? AND patient_id = ?
        FOR UPDATE
        """;
    static final String FIND_PATIENT_HOLD_SQL = """
        SELECT doctor_id, appointment_date, slot_number
        FROM public.appointment_holds
        WHERE hold_id = ? AND patient_id = ?
        """;
    static final String EXPIRE_HOLD_SQL = """
        UPDATE public.appointment_holds
        SET status = 'EXPIRED'
        WHERE hold_id = ? AND patient_id = ? AND status = 'ACTIVE'
          AND expires_at <= CURRENT_TIMESTAMP
        """;
    static final String CONVERT_HOLD_SQL = """
        UPDATE public.appointment_holds
        SET status = 'CONVERTED'
        WHERE hold_id = ? AND patient_id = ? AND status = 'ACTIVE'
          AND expires_at > CURRENT_TIMESTAMP
        """;
    static final String RELEASE_HOLD_SQL = """
        UPDATE public.appointment_holds
        SET status = CASE WHEN expires_at <= CURRENT_TIMESTAMP
                          THEN 'EXPIRED' ELSE 'RELEASED' END
        WHERE hold_id = ? AND patient_id = ? AND status = 'ACTIVE'
        """;
    static final String ACTIVE_HOLDS_SQL = """
        SELECT patient_id, slot_number
        FROM public.appointment_holds
        WHERE (doctor_id = ? OR patient_id = ?) AND appointment_date = ?
          AND status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP
        """;
    static final String INSERT_SQL = """
        INSERT INTO public.appointments
            (patient_id, doctor_id, appointment_type, appointment_date,
             slot_number, status, reason)
        VALUES (?, ?, 'CONSULTATION', ?, ?, 'PENDING_RECEPTIONIST', ?)
        RETURNING appointment_id
        """;
    static final String ACTIVE_DOCTOR_SQL = """
        SELECT 1
        FROM public.doctors d
        JOIN public.users u ON u.user_id = d.user_id
        WHERE d.doctor_id = ? AND u.role = 'DOCTOR' AND u.is_active = TRUE
        """;
    static final String ELIGIBLE_PROPOSAL_PATIENT_SQL = """
        SELECT 1
        FROM public.appointments a
        JOIN public.users p ON p.user_id = a.patient_id
        JOIN public.doctors d ON d.doctor_id = a.doctor_id
        JOIN public.users du ON du.user_id = d.user_id
        WHERE a.doctor_id = ? AND a.patient_id = ?
          AND a.appointment_type = 'CONSULTATION' AND a.status = 'COMPLETED'
          AND p.role = 'PATIENT' AND p.is_active = TRUE
          AND du.role = 'DOCTOR' AND du.is_active = TRUE
        LIMIT 1
        """;
    static final String INSERT_DOCTOR_PROPOSAL_SQL = """
        INSERT INTO public.appointments
            (patient_id, doctor_id, appointment_type, appointment_date,
             slot_number, status, reason)
        VALUES (?, ?, ?, ?, ?, 'PENDING_PATIENT', ?)
        RETURNING appointment_id
        """;
    static final String LOCK_PATIENT_PROPOSAL_SQL = """
        SELECT appointment_id, patient_id, doctor_id, appointment_type,
               appointment_date, slot_number, status
        FROM public.appointments
        WHERE appointment_id = ? AND patient_id = ?
        FOR UPDATE
        """;
    static final String CONFIRM_PROPOSAL_SQL = """
        UPDATE public.appointments
        SET status = 'CONFIRMED', appointment_date = ?, slot_number = ?
        WHERE appointment_id = ? AND patient_id = ? AND status = 'PENDING_PATIENT'
        """;
    static final String DECLINE_PROPOSAL_SQL = """
        UPDATE public.appointments
        SET status = 'DECLINED'
        WHERE appointment_id = ? AND patient_id = ? AND status = 'PENDING_PATIENT'
        """;
    static final String PATIENT_PROFILE_SQL = """
        SELECT u.user_id, u.first_name, u.last_name, u.email, u.phone,
               p.profile_id, p.birth_date, p.sex, p.address, p.blood_type,
               p.allergies, p.conditions, p.medications, p.medical_history
        FROM public.users u
        LEFT JOIN public.patient_profiles p ON p.user_id = u.user_id
        WHERE u.user_id = ? AND u.role = 'PATIENT'
        """;
    static final String LOCK_PATIENT_SQL = """
        SELECT user_id
        FROM public.users
        WHERE user_id = ? AND role = 'PATIENT'
        FOR UPDATE
        """;
    static final String APPOINTMENT_RECIPIENTS_SQL = """
        SELECT a.patient_id, d.user_id AS doctor_user_id
        FROM public.appointments a
        JOIN public.doctors d ON d.doctor_id = a.doctor_id
        WHERE a.appointment_id = ?
        """;
    static final String LOCK_PROFILE_SQL = """
        SELECT profile_id
        FROM public.patient_profiles
        WHERE user_id = ?
        FOR UPDATE
        """;
    static final String UPDATE_USER_PROFILE_SQL = """
        UPDATE public.users
        SET first_name = ?, last_name = ?, phone = ?
        WHERE user_id = ? AND role = 'PATIENT'
        """;
    static final String UPDATE_PATIENT_PROFILE_SQL = """
        UPDATE public.patient_profiles
        SET birth_date = ?, sex = ?, address = ?, blood_type = ?,
            allergies = ?, conditions = ?, medications = ?, medical_history = ?
        WHERE profile_id = ? AND user_id = ?
        """;
    static final String PATIENT_APPOINTMENTS_SQL = """
        SELECT a.appointment_id, a.patient_id, a.doctor_id, a.appointment_type,
               a.appointment_date, a.slot_number, a.status, a.reason,
               p.first_name AS patient_first_name, p.last_name AS patient_last_name,
               du.first_name AS doctor_first_name, du.last_name AS doctor_last_name,
               d.specialty
        FROM public.appointments a
        LEFT JOIN public.users p ON p.user_id = a.patient_id
        LEFT JOIN public.doctors d ON d.doctor_id = a.doctor_id
        LEFT JOIN public.users du ON du.user_id = d.user_id
        WHERE a.patient_id = ?
        ORDER BY CASE WHEN a.appointment_date >= CURRENT_DATE THEN 0 ELSE 1 END,
                 CASE WHEN a.appointment_date >= CURRENT_DATE
                      THEN a.appointment_date END ASC,
                 CASE WHEN a.appointment_date < CURRENT_DATE
                      THEN a.appointment_date END DESC,
                 a.slot_number, a.appointment_id
        """;
    static final String DOCTOR_APPOINTMENTS_SQL = """
        SELECT a.appointment_id, a.patient_id, a.doctor_id, a.appointment_type,
               a.appointment_date, a.slot_number, a.status, a.reason,
               p.first_name AS patient_first_name, p.last_name AS patient_last_name,
               du.first_name AS doctor_first_name, du.last_name AS doctor_last_name,
               d.specialty
        FROM public.appointments a
        LEFT JOIN public.users p ON p.user_id = a.patient_id
        LEFT JOIN public.doctors d ON d.doctor_id = a.doctor_id
        LEFT JOIN public.users du ON du.user_id = d.user_id
        WHERE a.doctor_id = ?
        ORDER BY CASE WHEN a.appointment_date >= CURRENT_DATE THEN 0 ELSE 1 END,
                 CASE WHEN a.appointment_date >= CURRENT_DATE
                      THEN a.appointment_date END ASC,
                 CASE WHEN a.appointment_date < CURRENT_DATE
                      THEN a.appointment_date END DESC,
                 a.slot_number, a.appointment_id
        """;
    static final String RECEPTIONIST_APPOINTMENTS_SQL = """
        SELECT a.appointment_id, a.patient_id, a.doctor_id, a.appointment_type,
               a.appointment_date, a.slot_number, a.status, a.reason,
               p.first_name AS patient_first_name, p.last_name AS patient_last_name,
               du.first_name AS doctor_first_name, du.last_name AS doctor_last_name,
               d.specialty
        FROM public.appointments a
        LEFT JOIN public.users p ON p.user_id = a.patient_id
        LEFT JOIN public.doctors d ON d.doctor_id = a.doctor_id
        LEFT JOIN public.users du ON du.user_id = d.user_id
        WHERE a.appointment_type = 'CONSULTATION'
          AND a.status = 'PENDING_RECEPTIONIST'
        ORDER BY a.appointment_date, a.slot_number, a.appointment_id
        """;
    static final String LOCK_APPOINTMENT_BY_PATIENT_SQL = """
        SELECT appointment_id, status, appointment_type, appointment_date, slot_number
        FROM public.appointments
        WHERE appointment_id = ? AND patient_id = ?
        FOR UPDATE
        """;
    static final String LOCK_APPOINTMENT_BY_DOCTOR_SQL = """
        SELECT appointment_id, status, appointment_type, appointment_date, slot_number
        FROM public.appointments
        WHERE appointment_id = ? AND doctor_id = ?
        FOR UPDATE
        """;
    static final String LOCK_PENDING_CONSULTATION_SQL = """
        SELECT appointment_id, status, appointment_type, appointment_date, slot_number
        FROM public.appointments
        WHERE appointment_id = ?
          AND appointment_type = 'CONSULTATION'
          AND status = 'PENDING_RECEPTIONIST'
        FOR UPDATE
        """;
    static final String UPDATE_APPOINTMENT_STATUS_SQL = """
        UPDATE public.appointments
        SET status = ?
        WHERE appointment_id = ? AND status = ?
        """;
    static final String LOCK_DOCTOR_SLOT_SQL = """
        SELECT
        to_regclass('public.appointment_holds') AS existing_holds_table
    """;
    static final String TIME_OFF_SLOT_CONFLICT_SQL = """
        SELECT 1 FROM public.appointments
        WHERE doctor_id = ? AND appointment_date = ? AND slot_number = ?
          AND status IN ('PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED')
        UNION ALL
        SELECT 1 FROM public.time_off_requests
        WHERE doctor_id = ? AND request_date = ? AND slot_number = ?
          AND status IN ('pending', 'approved')
        UNION ALL
        SELECT 1
        FROM public.appointment_change_requests r
        JOIN public.appointments a ON a.appointment_id = r.appointment_id
        WHERE a.doctor_id = ? AND r.kind = 'reschedule' AND r.status = 'pending'
          AND r.proposed_date = ? AND r.proposed_slot = ?
        LIMIT 1
        """;
    static final String INSERT_TIME_OFF_SQL = """
        INSERT INTO public.time_off_requests
            (doctor_id, request_date, slot_number, status, reason)
        VALUES (?, ?, ?, 'pending', ?)
        RETURNING time_off_id
        """;
    static final String DOCTOR_TIME_OFF_SQL = """
        SELECT time_off_id, doctor_id, request_date, slot_number, status, reason
        FROM public.time_off_requests
        WHERE doctor_id = ?
        ORDER BY request_date DESC, slot_number, time_off_id
        """;
    static final String PENDING_TIME_OFF_SQL = """
        SELECT t.time_off_id, t.doctor_id, t.request_date, t.slot_number,
               t.status, t.reason
        FROM public.time_off_requests t
        WHERE t.status = 'pending'
        ORDER BY t.request_date, t.slot_number, t.time_off_id
        """;
    static final String LOCK_TIME_OFF_SQL = """
        SELECT doctor_id, request_date, slot_number, status
        FROM public.time_off_requests
        WHERE time_off_id = ?
        FOR UPDATE
        """;
    static final String SET_TIME_OFF_DECISION_SQL = """
        UPDATE public.time_off_requests
        SET status = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP
        WHERE time_off_id = ? AND status = 'pending'
        """;
    static final String DOCTOR_USER_SQL = """
        SELECT user_id FROM public.doctors WHERE doctor_id = ?
        """;
    static final String RECEPTIONIST_USERS_SQL = """
        SELECT user_id FROM public.users
        WHERE role = 'RECEPTIONIST' AND is_active = TRUE
        """;
    static final String INSERT_NOTIFICATION_SQL = """
        INSERT INTO public.notifications (recipient_id, message)
        VALUES (?, ?)
        """;
    static final String INSERT_NOTIFICATIONS_FOR_ROLE_SQL = """
        INSERT INTO public.notifications (recipient_id, message)
        SELECT user_id, ? FROM public.users
        WHERE role = ? AND is_active = TRUE
        """;
    static final String PENDING_CHANGE_SQL = """
        SELECT r.request_id, r.appointment_id, r.requester_id, r.kind, r.details,
               r.status, r.proposed_date, r.proposed_slot,
               a.appointment_date, a.slot_number AS appointment_slot,
               a.appointment_type,
               ru.first_name AS requester_first_name, ru.last_name AS requester_last_name,
               pu.first_name AS patient_first_name, pu.last_name AS patient_last_name,
               du.first_name AS doctor_first_name, du.last_name AS doctor_last_name
        FROM public.appointment_change_requests r
        JOIN public.appointments a ON a.appointment_id = r.appointment_id
        JOIN public.users ru ON ru.user_id = r.requester_id
        JOIN public.users pu ON pu.user_id = a.patient_id
        JOIN public.doctors d ON d.doctor_id = a.doctor_id
        JOIN public.users du ON du.user_id = d.user_id
        WHERE r.status = 'pending'
        ORDER BY r.created_at, r.request_id
        """;
    static final String LOCK_PATIENT_APPOINTMENT_FOR_CHANGE_SQL = """
        SELECT appointment_id, patient_id, doctor_id, appointment_type,
               appointment_date, slot_number, status
        FROM public.appointments
        WHERE appointment_id = ? AND patient_id = ?
        FOR UPDATE
        """;
    static final String LOCK_DOCTOR_APPOINTMENT_FOR_CHANGE_SQL = """
        SELECT appointment_id, patient_id, doctor_id, appointment_type,
               appointment_date, slot_number, status
        FROM public.appointments
        WHERE appointment_id = ? AND doctor_id = ?
        FOR UPDATE
        """;
    static final String PENDING_DUPLICATE_CHANGE_SQL = """
        SELECT 1 FROM public.appointment_change_requests
        WHERE appointment_id = ? AND kind = ? AND status = 'pending'
        LIMIT 1
        """;
    static final String INSERT_CHANGE_SQL = """
        INSERT INTO public.appointment_change_requests
            (appointment_id, requester_id, kind, details, status, proposed_date, proposed_slot)
        VALUES (?, ?, ?, ?, 'pending', ?, ?)
        RETURNING request_id
        """;
    static final String LOCK_CHANGE_SQL = """
        SELECT request_id, appointment_id, requester_id, kind, status,
               proposed_date, proposed_slot
        FROM public.appointment_change_requests
        WHERE request_id = ?
        FOR UPDATE
        """;
    static final String LOCK_LINKED_APPOINTMENT_SQL = """
        SELECT appointment_id, patient_id, doctor_id, appointment_type,
               appointment_date, slot_number, status
        FROM public.appointments
        WHERE appointment_id = ?
        FOR UPDATE
        """;
    static final String UPDATE_APPOINTMENT_SLOT_SQL = """
        UPDATE public.appointments
        SET appointment_date = ?, slot_number = ?
        WHERE appointment_id = ? AND status = 'CONFIRMED'
        """;
    static final String UPDATE_CHANGE_DECISION_SQL = """
        UPDATE public.appointment_change_requests
        SET status = ?, resolution = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP
        WHERE request_id = ? AND status = 'pending'
        """;
    static final String NOTIFICATIONS_SQL = """
        SELECT notification_id, message, created_at, is_read
        FROM public.notifications
        WHERE recipient_id = ?
        ORDER BY created_at DESC, notification_id DESC
        """;
    static final String MARK_NOTIFICATION_READ_SQL = """
        UPDATE public.notifications SET is_read = TRUE
        WHERE notification_id = ? AND recipient_id = ?
        """;
    static final String DOCTOR_NOTIFY_APPOINTMENT_SQL = """
        SELECT patient_id FROM public.appointments
        WHERE appointment_id = ? AND doctor_id = ? AND status = 'CONFIRMED'
        FOR UPDATE
        """;
    static final String CHANGE_SLOT_CONFLICT_SQL = """
        SELECT 1 FROM public.appointments
        WHERE appointment_date = ? AND slot_number = ?
          AND status IN ('PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED')
          AND appointment_id <> ? AND (doctor_id = ? OR patient_id = ?)
        UNION ALL
        SELECT 1 FROM public.time_off_requests
        WHERE doctor_id = ? AND request_date = ? AND slot_number = ?
          AND status = 'approved'
        UNION ALL
        SELECT 1
        FROM public.appointment_change_requests r
        JOIN public.appointments a ON a.appointment_id = r.appointment_id
        WHERE r.kind = 'reschedule' AND r.status = 'pending'
          AND r.proposed_date = ? AND r.proposed_slot = ?
          AND r.appointment_id <> ? AND (a.doctor_id = ? OR a.patient_id = ?)
        LIMIT 1
        """;
    static final String ACTIVE_APPOINTMENT_OR_RESCHEDULE_SQL = """
        SELECT 1 FROM public.appointments
        WHERE doctor_id = ? AND appointment_date = ? AND slot_number = ?
          AND status IN ('PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED')
        UNION ALL
        SELECT 1
        FROM public.appointment_change_requests r
        JOIN public.appointments a ON a.appointment_id = r.appointment_id
        WHERE a.doctor_id = ? AND r.kind = 'reschedule' AND r.status = 'pending'
          AND r.proposed_date = ? AND r.proposed_slot = ?
        LIMIT 1
        """;

    private final AppointmentRepositoryConnectionProvider connectionProvider;

    public JdbcClyvrDataRepository(AppointmentRepositoryConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
    }

    @Override
    public Optional<UserCredential> findCredential(String login) throws SQLException {
        Objects.requireNonNull(login, "login");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREDENTIAL_SQL)) {
            statement.setString(1, login.trim());
            statement.setString(2, login.trim());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                long userId = requiredId(result, "user_id");
                UserCredential credential = new UserCredential(
                        userId,
                        result.getString("password_hash"),
                        parseRole(result.getString("role")),
                        result.getBoolean("is_active"),
                        nullableLong(result, "doctor_id"),
                        result.getString("first_name"),
                        result.getString("last_name"),
                        result.getString("email"),
                        result.getString("phone"));
                if (result.next()) {
                    throw new SQLException("Login identifier matches multiple database users.");
                }
                return Optional.of(credential);
            }
        }
    }

    @Override
    public Optional<UserCredential> findCredential(long userId) throws SQLException {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID is invalid.");
        }
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREDENTIAL_BY_ID_SQL)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                UserCredential credential = new UserCredential(
                        requiredId(result, "user_id"),
                        result.getString("password_hash"),
                        parseRole(result.getString("role")),
                        result.getBoolean("is_active"),
                        nullableLong(result, "doctor_id"),
                        result.getString("first_name"),
                        result.getString("last_name"),
                        result.getString("email"),
                        result.getString("phone"));
                if (result.next()) {
                    throw new SQLException("Database user has ambiguous doctor links.");
                }
                return Optional.of(credential);
            }
        }
    }

    @Override
    public long registerPatient(
            String email, String passwordHash, String firstName, String lastName,
            String phone, LocalDate birthDate, String sex, String address) throws SQLException {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(birthDate, "birthDate");
        boolean previousAutoCommit;
        try (Connection connection = connectionProvider.getConnection()) {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long userId;
                try (PreparedStatement user = connection.prepareStatement(INSERT_PATIENT_USER_SQL)) {
                    user.setString(1, email);
                    user.setString(2, email);
                    user.setString(3, passwordHash);
                    user.setString(4, firstName);
                    user.setString(5, lastName);
                    user.setString(6, phone);
                    try (ResultSet result = user.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Database did not return the registered user ID.");
                        }
                        userId = requiredId(result, "user_id");
                    }
                }
                try (PreparedStatement profile =
                             connection.prepareStatement(INSERT_PATIENT_PROFILE_SQL)) {
                    profile.setLong(1, userId);
                    profile.setObject(2, birthDate);
                    profile.setString(3, sex);
                    profile.setString(4, address);
                    if (profile.executeUpdate() != 1) {
                        throw new SQLException("Database did not create the patient profile.");
                    }
                }
                connection.commit();
                return userId;
            } catch (SQLException exception) {
                rollback(connection, exception);
                if ("23505".equals(exception.getSQLState())) {
                    throw new AccountAlreadyExistsException(exception);
                }
                throw exception;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    @Override
    public void updatePasswordHash(long userId, String passwordHash) throws SQLException {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID is invalid.");
        }
        Objects.requireNonNull(passwordHash, "passwordHash");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_PASSWORD_HASH_SQL)) {
            statement.setString(1, passwordHash);
            statement.setLong(2, userId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("The active user password could not be updated.");
            }
        }
    }

    @Override
    public Optional<ResolvedDatabaseIdentity> findActiveIdentity(long userId) throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(IDENTITY_SQL)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                long resolvedUserId = requiredId(result, "user_id");
                String rawRole = result.getString("role");
                boolean active = result.getBoolean("is_active");
                if (result.wasNull() || !active) {
                    return Optional.empty();
                }
                BackendRole role = parseRole(rawRole);
                long rawDoctorId = result.getLong("doctor_id");
                boolean doctorIdNull = result.wasNull();
                Long doctorId = doctorIdNull ? null : rawDoctorId;
                if (doctorId != null && doctorId <= 0) {
                    throw new SQLException("Database identity has an invalid doctor link.");
                }
                if (result.next()) {
                    throw new SQLException("Database identity has ambiguous doctor links.");
                }
                if ((role == BackendRole.DOCTOR) != (doctorId != null)) {
                    return Optional.empty();
                }
                return Optional.of(new ResolvedDatabaseIdentity(resolvedUserId, role, doctorId));
            }
        }
    }

    private static void notifyAppointmentStatus(
            Connection connection, long appointmentId, String status) throws SQLException {
        long patientId;
        long doctorUserId;
        try (PreparedStatement query =
                     connection.prepareStatement(APPOINTMENT_RECIPIENTS_SQL)) {
            query.setLong(1, appointmentId);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    throw new AppointmentNotFoundException();
                }
                patientId = requiredId(result, "patient_id");
                doctorUserId = requiredId(result, "doctor_user_id");
            }
        }
        String message = "Appointment #" + appointmentId + " status changed to "
                + status.replace('_', ' ') + ".";
        insertNotification(connection, patientId, message);
        if (doctorUserId != patientId) {
            insertNotification(connection, doctorUserId, message);
        }
    }

    @Override
    public List<BackendDoctorEntry> findMappedDoctors(Map<String, Long> mappedDoctorIds)
            throws SQLException {
        if (mappedDoctorIds.isEmpty()) {
            return List.of();
        }
        StringJoiner placeholders = new StringJoiner(", ");
        mappedDoctorIds.values().forEach(ignored -> placeholders.add("?"));
        String sql = DOCTOR_SQL_PREFIX + placeholders + DOCTOR_SQL_SUFFIX;

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (long doctorId : mappedDoctorIds.values()) {
                statement.setLong(parameter++, doctorId);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<BackendDoctorEntry> doctors = new ArrayList<>();
                Set<Long> returnedIds = new HashSet<>();
                while (result.next()) {
                    long doctorId = requiredId(result, "doctor_id");
                    if (!returnedIds.add(doctorId)) {
                        throw new SQLException("Database returned duplicate doctor records.");
                    }
                    String key = mappedDoctorIds.entrySet().stream()
                            .filter(entry -> entry.getValue() == doctorId)
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElseThrow(() -> new SQLException(
                                    "Database returned an unmapped doctor record."));
                    long userId = requiredId(result, "user_id");
                    if (!"DOCTOR".equalsIgnoreCase(result.getString("role"))) {
                        throw new SQLException("Mapped doctor is not linked to a doctor account.");
                    }
                    if (!result.getBoolean("is_active")) {
                        continue;
                    }
                    String firstName = result.getString("first_name");
                    String lastName = result.getString("last_name");
                    String specialty = result.getString("specialty");
                    if (firstName == null || lastName == null || specialty == null) {
                        throw new SQLException("Mapped doctor is missing directory information.");
                    }
                    doctors.add(new BackendDoctorEntry(
                            key, doctorId, userId, firstName, lastName, specialty));
                }
                if (!returnedIds.equals(new HashSet<>(mappedDoctorIds.values()))) {
                    throw new SQLException("One or more mapped doctors were not found.");
                }
                return List.copyOf(doctors);
            }
        }
    }

    @Override
    public BackendAvailability findAvailability(long patientId, long doctorId, LocalDate day)
            throws SQLException {
        AvailabilityState[] states = new AvailabilityState[8];
        java.util.Arrays.fill(states, AvailabilityState.AVAILABLE);
        try (Connection connection = connectionProvider.getConnection()) {
            readAppointments(connection, patientId, doctorId, day, states);
            readApprovedTimeOff(connection, doctorId, day, states);
            readPendingRescheduleHolds(connection, patientId, doctorId, day, states);
            readActiveHolds(connection, patientId, doctorId, day, states);
        }
        List<BackendAvailability.Slot> slots = new ArrayList<>(8);
        for (int slot = 0; slot < states.length; slot++) {
            slots.add(new BackendAvailability.Slot(slot, states[slot].name()));
        }
        return new BackendAvailability(doctorId, slots);
    }

    @Override
    public BackendAvailability findDoctorAvailability(long doctorId, LocalDate day)
            throws SQLException {
        if (doctorId <= 0 || day == null) {
            throw new IllegalArgumentException("Doctor and schedule date are required.");
        }
        AvailabilityState[] states = new AvailabilityState[8];
        java.util.Arrays.fill(states, AvailabilityState.AVAILABLE);
        try (Connection connection = connectionProvider.getConnection()) {
            readDoctorAppointments(connection, doctorId, day, states);
            readApprovedTimeOff(connection, doctorId, day, states);
            readDoctorRescheduleHolds(connection, doctorId, day, states);
            readDoctorActiveHolds(connection, doctorId, day, states);
        }
        List<BackendAvailability.Slot> slots = new ArrayList<>(8);
        for (int slot = 0; slot < states.length; slot++) {
            slots.add(new BackendAvailability.Slot(slot, states[slot].name()));
        }
        return new BackendAvailability(doctorId, slots);
    }

    @Override
    public List<BackendConsultationRequest> findPendingConsultations() throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(PENDING_SQL);
             ResultSet result = statement.executeQuery()) {
            List<BackendConsultationRequest> requests = new ArrayList<>();
            while (result.next()) {
                requests.add(new BackendConsultationRequest(
                        requiredId(result, "appointment_id"),
                        requiredId(result, "patient_id"),
                        requiredId(result, "doctor_id"),
                        result.getObject("appointment_date", LocalDate.class),
                        requiredSlot(result, "slot_number"),
                        result.getString("reason")));
            }
            return List.copyOf(requests);
        }
    }

    @Override
    public Optional<BackendPatientProfile> findPatientProfile(long userId) throws SQLException {
        try (Connection connection = connectionProvider.getConnection()) {
            return readPatientProfile(connection, userId);
        }
    }

    @Override
    public BackendPatientProfile updatePatientProfile(
            long userId, PatientProfileUpdate update) throws SQLException {
        Objects.requireNonNull(update);
        try (Connection connection = connectionProvider.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement userLock =
                             connection.prepareStatement(LOCK_PATIENT_SQL)) {
                    userLock.setLong(1, userId);
                    try (ResultSet result = userLock.executeQuery()) {
                        if (!result.next()) {
                            throw new PatientProfileNotFoundException();
                        }
                    }
                }

                long profileId = lockSingleProfile(connection, userId);
                try (PreparedStatement userUpdate =
                             connection.prepareStatement(UPDATE_USER_PROFILE_SQL)) {
                    userUpdate.setString(1, update.firstName());
                    userUpdate.setString(2, update.lastName());
                    userUpdate.setString(3, update.phone());
                    userUpdate.setLong(4, userId);
                    requireOneUpdated(userUpdate.executeUpdate());
                }
                try (PreparedStatement profileUpdate =
                             connection.prepareStatement(UPDATE_PATIENT_PROFILE_SQL)) {
                    profileUpdate.setObject(1, update.birthDate().isBlank()
                            ? null : LocalDate.parse(update.birthDate()));
                    profileUpdate.setString(2, update.sex());
                    profileUpdate.setString(3, update.address());
                    profileUpdate.setString(4, update.bloodType());
                    profileUpdate.setString(5, update.allergies());
                    profileUpdate.setString(6, update.conditions());
                    profileUpdate.setString(7, update.medications());
                    profileUpdate.setString(8, update.medicalHistory());
                    profileUpdate.setLong(9, profileId);
                    profileUpdate.setLong(10, userId);
                    requireOneUpdated(profileUpdate.executeUpdate());
                }
                BackendPatientProfile saved = readPatientProfile(connection, userId)
                        .orElseThrow(PatientProfileNotFoundException::new);
                connection.commit();
                return saved;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    @Override
    public List<BackendAppointment> findAppointments(
            BackendRole role, long userId, Long doctorId) throws SQLException {
        String sql = appointmentListSql(role);
        if (sql == null || (role == BackendRole.DOCTOR && doctorId == null)) {
            throw new IllegalArgumentException("The signed-in role cannot list appointments.");
        }
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (role == BackendRole.DOCTOR) {
                statement.setLong(1, doctorId);
            } else if (role == BackendRole.PATIENT) {
                statement.setLong(1, userId);
            }
            try (ResultSet result = statement.executeQuery()) {
                return mapAppointments(result);
            }
        }
    }

    @Override
    public Optional<BackendAppointment> findAppointment(
            BackendRole role, long userId, Long doctorId, long appointmentId)
            throws SQLException {
        String listSql = appointmentListSql(role);
        if (listSql == null || (role == BackendRole.DOCTOR && doctorId == null)) {
            return Optional.empty();
        }
        int orderIndex = listSql.lastIndexOf("ORDER BY");
        if (orderIndex < 0) {
            throw new SQLException("Appointment query is missing its stable ordering.");
        }
        int orderLineStart = listSql.lastIndexOf('\n', orderIndex);
        String sql = listSql.substring(0, orderLineStart)
                + " AND a.appointment_id = ?"
                + listSql.substring(orderLineStart);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (role == BackendRole.DOCTOR) {
                statement.setLong(1, doctorId);
            } else if (role == BackendRole.PATIENT) {
                statement.setLong(1, userId);
            }
            int idParameter = role == BackendRole.RECEPTIONIST ? 1 : 2;
            statement.setLong(idParameter, appointmentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                BackendAppointment appointment = mapAppointment(result);
                if (result.next()) {
                    throw new SQLException("Database returned duplicate appointment records.");
                }
                return Optional.of(appointment);
            }
        }
    }

    @Override
    public void decidePendingConsultation(long appointmentId, boolean approved)
            throws SQLException {
        updateAppointmentStatus(
                LOCK_PENDING_CONSULTATION_SQL, 0, appointmentId,
                approved ? "CONFIRMED" : "REJECTED", "PENDING_RECEPTIONIST", null);
    }

    @Override
    public void cancelPatientAppointment(long userId, long appointmentId) throws SQLException {
        updateAppointmentStatus(
                LOCK_APPOINTMENT_BY_PATIENT_SQL, userId, appointmentId,
                "CANCELLED", "CONFIRMED", null);
    }

    @Override
    public void finishDoctorAppointment(
            long doctorId, long appointmentId, String newStatus) throws SQLException {
        if (!"COMPLETED".equals(newStatus) && !"NO_SHOW".equals(newStatus)) {
            throw new IllegalArgumentException("The requested doctor status is not supported.");
        }
        updateAppointmentStatus(
                LOCK_APPOINTMENT_BY_DOCTOR_SQL, doctorId, appointmentId,
                newStatus, "CONFIRMED", newStatus);
    }

    @Override
    public List<BackendTimeOffRequest> findDoctorTimeOff(long doctorId) throws SQLException {
        if (doctorId <= 0) {
            throw new IllegalArgumentException("Doctor ID must be positive.");
        }
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(DOCTOR_TIME_OFF_SQL)) {
            statement.setLong(1, doctorId);
            try (ResultSet result = statement.executeQuery()) {
                List<BackendTimeOffRequest> requests = new ArrayList<>();
                while (result.next()) {
                    requests.add(mapTimeOff(result));
                }
                return List.copyOf(requests);
            }
        }
    }

    @Override
    public List<BackendTimeOffRequest> findPendingTimeOff() throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(PENDING_TIME_OFF_SQL);
             ResultSet result = statement.executeQuery()) {
            List<BackendTimeOffRequest> requests = new ArrayList<>();
            while (result.next()) {
                requests.add(mapTimeOff(result));
            }
            return List.copyOf(requests);
        }
    }

    @Override
    public long submitTimeOff(long doctorId, LocalDate day, int slot, String reason)
            throws SQLException {
        validateFutureSlot(day, slot, reason);
        if (doctorId <= 0 || reason.length() > 2000) {
            throw new IllegalArgumentException("Time-off request is invalid.");
        }
        return inTransaction(connection -> {
            acquireDoctorSlotLock(connection, doctorId, day, slot);
            if (hasTimeOffConflict(connection, doctorId, day, slot)) {
                throw new AppointmentConflictException();
            }
            long requestId;
            try (PreparedStatement insert = connection.prepareStatement(INSERT_TIME_OFF_SQL)) {
                insert.setLong(1, doctorId);
                insert.setObject(2, day);
                insert.setInt(3, slot);
                insert.setString(4, reason);
                try (ResultSet result = insert.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("Database did not return the time-off request ID.");
                    }
                    requestId = requiredId(result, "time_off_id");
                }
            }
            insertNotificationsForRole(connection, "RECEPTIONIST",
                    "A doctor submitted a time-off request for " + day + ".");
            return requestId;
        });
    }

    @Override
    public void decideTimeOff(
            long receptionistId, long timeOffId, boolean approved, String reason)
            throws SQLException {
        if (receptionistId <= 0 || timeOffId <= 0 || reason == null || reason.length() > 2000) {
            throw new IllegalArgumentException("Time-off decision is invalid.");
        }
        inTransaction(connection -> {
            long doctorId;
            LocalDate day;
            int slot;
            try (PreparedStatement lock = connection.prepareStatement(LOCK_TIME_OFF_SQL)) {
                lock.setLong(1, timeOffId);
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next()) {
                        throw new AppointmentNotFoundException();
                    }
                    doctorId = requiredId(result, "doctor_id");
                    day = requiredDate(result, "request_date");
                    slot = requiredSlot(result, "slot_number");
                    if (!"pending".equals(result.getString("status"))) {
                        throw new InvalidAppointmentTransitionException();
                    }
                }
            }
            acquireDoctorSlotLock(connection, doctorId, day, slot);
            if (approved && hasActiveAppointmentOrReschedule(connection, doctorId, day, slot)) {
                throw new AppointmentConflictException();
            }
            try (PreparedStatement update = connection.prepareStatement(SET_TIME_OFF_DECISION_SQL)) {
                update.setString(1, approved ? "approved" : "rejected");
                update.setLong(2, receptionistId);
                update.setLong(3, timeOffId);
                requireOneUpdated(update.executeUpdate());
            }
            long doctorUserId = doctorUserId(connection, doctorId);
            insertNotification(connection, doctorUserId,
                    "Your time-off request for " + day + " was "
                            + (approved ? "approved." : "rejected.")
                            + (reason.isBlank() ? "" : " " + reason));
            return null;
        });
    }

    @Override
    public List<BackendAppointmentChangeRequest> findPendingAppointmentChanges()
            throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(PENDING_CHANGE_SQL);
             ResultSet result = statement.executeQuery()) {
            List<BackendAppointmentChangeRequest> requests = new ArrayList<>();
            while (result.next()) {
                requests.add(mapChangeRequest(result));
            }
            return List.copyOf(requests);
        }
    }

    @Override
    public long submitAppointmentChange(
            BackendRole role,
            long userId,
            Long doctorId,
            long appointmentId,
            String kind,
            String details,
            LocalDate proposedDate,
            Integer proposedSlot) throws SQLException {
        if (role == null || userId <= 0 || appointmentId <= 0 || details == null
                || details.isBlank() || details.length() > 2000
                || !Set.of("cancellation", "reschedule").contains(kind)) {
            throw new IllegalArgumentException("Appointment-change request is invalid.");
        }
        if ((role == BackendRole.PATIENT && doctorId != null)
                || (role == BackendRole.DOCTOR && (doctorId == null || !"cancellation".equals(kind)))
                || (role != BackendRole.PATIENT && role != BackendRole.DOCTOR)) {
            throw new IllegalArgumentException("The signed-in role cannot submit this request.");
        }
        if ("reschedule".equals(kind)) {
            validateFutureSlot(proposedDate, proposedSlot == null ? -1 : proposedSlot, details);
        } else if (proposedDate != null || proposedSlot != null) {
            throw new IllegalArgumentException("Cancellation requests cannot include a replacement slot.");
        }
        return inTransaction(connection -> {
            String lockSql = role == BackendRole.PATIENT
                    ? LOCK_PATIENT_APPOINTMENT_FOR_CHANGE_SQL
                    : LOCK_DOCTOR_APPOINTMENT_FOR_CHANGE_SQL;
            LockedChangeAppointment appointment;
            try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
                lock.setLong(1, appointmentId);
                lock.setLong(2, role == BackendRole.PATIENT ? userId : doctorId);
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next()) {
                        throw new AppointmentNotFoundException();
                    }
                    appointment = readLockedChangeAppointment(result);
                }
            }
            if (!"CONFIRMED".equals(appointment.status())) {
                throw new InvalidAppointmentTransitionException();
            }
            if ("reschedule".equals(kind)) {
                if (!slotMatchesType(appointment.appointmentType(), proposedSlot)
                        || (proposedDate.equals(appointment.day())
                            && proposedSlot == appointment.slot())) {
                    throw new IllegalArgumentException(
                            "Choose a different replacement slot matching the appointment type.");
                }
                acquireSlotLocks(connection, appointment.patientId(), appointment.doctorId(),
                        proposedDate, proposedSlot);
                if (hasConflictExcludingAppointment(connection, appointment.patientId(),
                        appointment.doctorId(), proposedDate, proposedSlot, appointmentId)) {
                    throw new AppointmentConflictException();
                }
            }
            try (PreparedStatement duplicate =
                         connection.prepareStatement(PENDING_DUPLICATE_CHANGE_SQL)) {
                duplicate.setLong(1, appointmentId);
                duplicate.setString(2, kind);
                try (ResultSet result = duplicate.executeQuery()) {
                    if (result.next()) {
                        throw new InvalidAppointmentTransitionException();
                    }
                }
            }
            long requestId;
            try (PreparedStatement insert = connection.prepareStatement(INSERT_CHANGE_SQL)) {
                insert.setLong(1, appointmentId);
                insert.setLong(2, userId);
                insert.setString(3, kind);
                insert.setString(4, details);
                insert.setObject(5, proposedDate);
                if (proposedSlot == null) {
                    insert.setNull(6, java.sql.Types.SMALLINT);
                } else {
                    insert.setInt(6, proposedSlot);
                }
                try (ResultSet result = insert.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("Database did not return the change request ID.");
                    }
                    requestId = requiredId(result, "request_id");
                }
            }
            insertNotificationsForRole(connection, "RECEPTIONIST",
                    "A " + kind + " request was submitted for appointment #"
                            + appointmentId + ".");
            insertNotification(connection, userId,
                    "Your " + kind + " request for appointment #" + appointmentId
                            + " was sent for receptionist review.");
            return requestId;
        });
    }

    @Override
    public void decideAppointmentChange(
            long receptionistId, long requestId, boolean approved, String resolution)
            throws SQLException {
        if (receptionistId <= 0 || requestId <= 0 || resolution == null
                || resolution.length() > 2000) {
            throw new IllegalArgumentException("Appointment-change decision is invalid.");
        }
        inTransaction(connection -> {
            long appointmentId;
            long requesterId;
            String kind;
            LocalDate proposedDate;
            Integer proposedSlot;
            try (PreparedStatement lock = connection.prepareStatement(LOCK_CHANGE_SQL)) {
                lock.setLong(1, requestId);
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next()) {
                        throw new AppointmentNotFoundException();
                    }
                    appointmentId = requiredId(result, "appointment_id");
                    requesterId = requiredId(result, "requester_id");
                    kind = result.getString("kind");
                    if (!"pending".equals(result.getString("status"))
                            || !Set.of("cancellation", "reschedule").contains(kind)) {
                        throw new InvalidAppointmentTransitionException();
                    }
                    proposedDate = result.getObject("proposed_date", LocalDate.class);
                    Object rawSlot = result.getObject("proposed_slot");
                    proposedSlot = rawSlot == null ? null : ((Number) rawSlot).intValue();
                }
            }
            LockedChangeAppointment appointment;
            try (PreparedStatement lock = connection.prepareStatement(LOCK_LINKED_APPOINTMENT_SQL)) {
                lock.setLong(1, appointmentId);
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next()) {
                        throw new AppointmentNotFoundException();
                    }
                    appointment = readLockedChangeAppointment(result);
                }
            }
            if (!"CONFIRMED".equals(appointment.status())) {
                throw new InvalidAppointmentTransitionException();
            }
            if (approved && "cancellation".equals(kind)) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE public.appointments SET status = 'CANCELLED' "
                        + "WHERE appointment_id = ? AND status = 'CONFIRMED'")) {
                    update.setLong(1, appointmentId);
                    requireOneUpdated(update.executeUpdate());
                }
            } else if (approved && "reschedule".equals(kind)) {
                if (proposedDate == null || proposedSlot == null
                        || !isValidFutureClinicSlot(proposedDate, proposedSlot)
                        || !slotMatchesType(appointment.appointmentType(), proposedSlot)) {
                    throw new InvalidAppointmentTransitionException();
                }
                acquireSlotLocks(connection, appointment.patientId(), appointment.doctorId(),
                        proposedDate, proposedSlot);
                if (hasConflictExcludingAppointment(connection, appointment.patientId(),
                        appointment.doctorId(), proposedDate, proposedSlot, appointmentId)) {
                    throw new AppointmentConflictException();
                }
                try (PreparedStatement update =
                             connection.prepareStatement(UPDATE_APPOINTMENT_SLOT_SQL)) {
                    update.setObject(1, proposedDate);
                    update.setInt(2, proposedSlot);
                    update.setLong(3, appointmentId);
                    requireOneUpdated(update.executeUpdate());
                }
            }
            try (PreparedStatement update = connection.prepareStatement(UPDATE_CHANGE_DECISION_SQL)) {
                update.setString(1, approved ? "approved" : "rejected");
                update.setString(2, resolution);
                update.setLong(3, receptionistId);
                update.setLong(4, requestId);
                requireOneUpdated(update.executeUpdate());
            }
            long patientId = appointment.patientId();
            long doctorUserId = doctorUserId(connection, appointment.doctorId());
            insertNotification(connection, requesterId,
                    "Your " + kind + " request for appointment #" + appointmentId + " was "
                            + (approved ? "approved." : "rejected.")
                            + (resolution.isBlank() ? "" : " " + resolution));
            long otherUserId = requesterId == patientId ? doctorUserId : patientId;
            if (otherUserId != requesterId) {
                insertNotification(connection, otherUserId,
                        "A " + kind + " request for appointment #" + appointmentId + " was "
                                + (approved ? "approved." : "rejected."));
            }
            return null;
        });
    }

    @Override
    public List<BackendNotification> findNotifications(long recipientId) throws SQLException {
        if (recipientId <= 0) {
            throw new IllegalArgumentException("Recipient ID must be positive.");
        }
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(NOTIFICATIONS_SQL)) {
            statement.setLong(1, recipientId);
            try (ResultSet result = statement.executeQuery()) {
                List<BackendNotification> notifications = new ArrayList<>();
                while (result.next()) {
                    OffsetDateTime created = result.getObject("created_at", OffsetDateTime.class);
                    if (created == null) {
                        throw new SQLException("Database returned a notification without a timestamp.");
                    }
                    notifications.add(new BackendNotification(
                            requiredId(result, "notification_id"),
                            requiredText(result, "message"), created,
                            result.getBoolean("is_read")));
                }
                return List.copyOf(notifications);
            }
        }
    }

    @Override
    public void markNotificationRead(long recipientId, long notificationId) throws SQLException {
        if (recipientId <= 0 || notificationId <= 0) {
            throw new IllegalArgumentException("Notification ID is invalid.");
        }
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(MARK_NOTIFICATION_READ_SQL)) {
            statement.setLong(1, notificationId);
            statement.setLong(2, recipientId);
            if (statement.executeUpdate() != 1) {
                throw new AppointmentNotFoundException();
            }
        }
    }

    @Override
    public void notifyUser(long senderId, long appointmentId, String message) throws SQLException {
        if (senderId <= 0 || appointmentId <= 0 || message == null
                || message.isBlank() || message.length() > 1000) {
            throw new IllegalArgumentException("Appointment notification is invalid.");
        }
        inTransaction(connection -> {
            long patientId;
            try (PreparedStatement statement =
                         connection.prepareStatement(DOCTOR_NOTIFY_APPOINTMENT_SQL)) {
                statement.setLong(1, appointmentId);
                statement.setLong(2, senderId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new AppointmentNotFoundException();
                    }
                    patientId = requiredId(result, "patient_id");
                }
            }
            insertNotification(connection, patientId, message);
            return null;
        });
    }

    @Override
    public long insertConsultation(
            long patientId, long doctorId, LocalDate day, int slot, String reason)
            throws SQLException {
        validateConsultation(patientId, doctorId, day, slot, reason);
        try (Connection connection = connectionProvider.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                requireActiveDoctor(connection, doctorId);
                acquireSlotLocks(connection, patientId, doctorId, day, slot);
                expireSlotHolds(connection, patientId, doctorId, day, slot);
                if (hasConflict(connection, patientId, doctorId, day, slot, null)) {
                    throw new AppointmentConflictException();
                }
                long appointmentId = insertConsultation(
                        connection, patientId, doctorId, day, slot, reason);
                insertNotification(connection, patientId,
                        "Consultation requested. Await receptionist approval.");
                insertNotificationsForRole(connection, "RECEPTIONIST",
                        "A new consultation request needs review.");
                connection.commit();
                return appointmentId;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    @Override
    public AppointmentHold createAppointmentHold(
            long patientId, long doctorId, LocalDate day, int slot) throws SQLException {
        validateConsultation(patientId, doctorId, day, slot, "hold");
        try (Connection connection = connectionProvider.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                acquireSlotLocks(connection, patientId, doctorId, day, slot);
                expireSlotHolds(connection, patientId, doctorId, day, slot);
                requireActiveDoctor(connection, doctorId);
                if (hasConflict(connection, patientId, doctorId, day, slot, null)) {
                    throw new AppointmentConflictException();
                }
                AppointmentHold hold;
                try (PreparedStatement insert = connection.prepareStatement(INSERT_HOLD_SQL)) {
                    insert.setLong(1, patientId);
                    insert.setLong(2, doctorId);
                    insert.setObject(3, day);
                    insert.setInt(4, slot);
                    try (ResultSet result = insert.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Database did not return the appointment hold.");
                        }
                        hold = new AppointmentHold(
                                requiredId(result, "hold_id"),
                                result.getObject("expires_at", OffsetDateTime.class));
                    }
                }
                connection.commit();
                return hold;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    @Override
    public long confirmAppointmentHold(long patientId, long holdId, String reason)
            throws SQLException {
        if (patientId <= 0 || holdId <= 0 || reason == null
                || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("Appointment confirmation is invalid.");
        }
        boolean expired = false;
        try (Connection connection = connectionProvider.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long doctorId;
                LocalDate day;
                int slot;
                try (PreparedStatement find = connection.prepareStatement(FIND_PATIENT_HOLD_SQL)) {
                    find.setLong(1, holdId);
                    find.setLong(2, patientId);
                    try (ResultSet result = find.executeQuery()) {
                        if (!result.next()) {
                            throw new AppointmentNotFoundException();
                        }
                        doctorId = requiredId(result, "doctor_id");
                        day = requiredDate(result, "appointment_date");
                        slot = requiredSlot(result, "slot_number");
                    }
                }
                acquireSlotLocks(connection, patientId, doctorId, day, slot);
                try (PreparedStatement lock =
                             connection.prepareStatement(LOCK_PATIENT_HOLD_SQL)) {
                    lock.setLong(1, holdId);
                    lock.setLong(2, patientId);
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next()) {
                            throw new AppointmentNotFoundException();
                        }
                        if (!"ACTIVE".equals(result.getString("status"))) {
                            throw new InvalidAppointmentTransitionException();
                        }
                        expired = !result.getBoolean("is_unexpired");
                    }
                }
                if (expired) {
                    try (PreparedStatement expire = connection.prepareStatement(EXPIRE_HOLD_SQL)) {
                        expire.setLong(1, holdId);
                        expire.setLong(2, patientId);
                        requireOneUpdated(expire.executeUpdate());
                    }
                    connection.commit();
                } else {
                    requireActiveDoctor(connection, doctorId);
                    expireSlotHolds(connection, patientId, doctorId, day, slot, holdId);
                    if (hasConflict(connection, patientId, doctorId, day, slot, holdId)) {
                        throw new AppointmentConflictException();
                    }
                    try (PreparedStatement convert = connection.prepareStatement(CONVERT_HOLD_SQL)) {
                        convert.setLong(1, holdId);
                        convert.setLong(2, patientId);
                        if (convert.executeUpdate() != 1) {
                            try (PreparedStatement expire =
                                         connection.prepareStatement(EXPIRE_HOLD_SQL)) {
                                expire.setLong(1, holdId);
                                expire.setLong(2, patientId);
                                expired = expire.executeUpdate() == 1;
                            }
                            if (!expired) {
                                throw new InvalidAppointmentTransitionException();
                            }
                            connection.commit();
                        } else {
                            long appointmentId = insertConsultation(
                                    connection, patientId, doctorId, day, slot, reason);
                            insertNotification(connection, patientId,
                                    "Consultation requested. Await receptionist approval.");
                            insertNotificationsForRole(connection, "RECEPTIONIST",
                                    "A new consultation request needs review.");
                            connection.commit();
                            return appointmentId;
                        }
                    }
                }
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
        if (expired) {
            throw new AppointmentHoldExpiredException();
        }
        throw new InvalidAppointmentTransitionException();
    }

    @Override
    public boolean releaseAppointmentHold(long patientId, long holdId) throws SQLException {
        if (patientId <= 0 || holdId <= 0) {
            throw new IllegalArgumentException("Appointment hold is invalid.");
        }
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement release = connection.prepareStatement(RELEASE_HOLD_SQL)) {
            release.setLong(1, holdId);
            release.setLong(2, patientId);
            int updated = release.executeUpdate();
            if (updated > 1) {
                throw new SQLException("Database updated multiple appointment holds.");
            }
            return updated == 1;
        }
    }

    @Override
    public long createDoctorProposal(
            long doctorId,
            long patientId,
            String appointmentType,
            LocalDate day,
            int slot,
            String reason) throws SQLException {
        validateDoctorProposal(doctorId, patientId, appointmentType, day, slot, reason);
        try (Connection connection = connectionProvider.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                requireEligibleProposalPatient(connection, doctorId, patientId);
                acquireSlotLocks(connection, patientId, doctorId, day, slot);
                expireSlotHolds(connection, patientId, doctorId, day, slot);
                if (hasConflict(connection, patientId, doctorId, day, slot, null)) {
                    throw new AppointmentConflictException();
                }
                long appointmentId;
                try (PreparedStatement insert =
                             connection.prepareStatement(INSERT_DOCTOR_PROPOSAL_SQL)) {
                    insert.setLong(1, patientId);
                    insert.setLong(2, doctorId);
                    insert.setString(3, appointmentType);
                    insert.setObject(4, day);
                    insert.setInt(5, slot);
                    insert.setString(6, reason);
                    try (ResultSet result = insert.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException(
                                    "Database did not return the created proposal.");
                        }
                        appointmentId = requiredId(result, "appointment_id");
                    }
                }
                insertNotification(connection, patientId,
                        "Your doctor proposed a " + appointmentType.replace('_', ' ').toLowerCase()
                                + " appointment. Please review it.");
                connection.commit();
                return appointmentId;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    @Override
    public void decideDoctorProposal(
            long patientId,
            long appointmentId,
            boolean approved,
            LocalDate alternativeDate,
            Integer alternativeSlot) throws SQLException {
        if (patientId <= 0 || appointmentId <= 0) {
            throw new IllegalArgumentException("Proposal decision is invalid.");
        }
        if ((alternativeDate == null) != (alternativeSlot == null)) {
            throw new IllegalArgumentException("An alternative date and slot must be provided together.");
        }
        if (!approved && alternativeDate != null) {
            throw new IllegalArgumentException("A declined proposal cannot include an alternative slot.");
        }
        try (Connection connection = connectionProvider.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                LockedProposal proposal;
                try (PreparedStatement lock =
                             connection.prepareStatement(LOCK_PATIENT_PROPOSAL_SQL)) {
                    lock.setLong(1, appointmentId);
                    lock.setLong(2, patientId);
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next()) {
                            throw new AppointmentNotFoundException();
                        }
                        proposal = new LockedProposal(
                                requiredId(result, "patient_id"),
                                requiredId(result, "doctor_id"),
                                result.getString("appointment_type"),
                                requiredDate(result, "appointment_date"),
                                requiredSlot(result, "slot_number"),
                                result.getString("status"));
                    }
                }
                if (!"PENDING_PATIENT".equals(proposal.status())
                        || !Set.of("FOLLOW_UP", "PROCEDURE").contains(proposal.appointmentType())) {
                    throw new InvalidAppointmentTransitionException();
                }
                if (approved) {
                    LocalDate appointmentDate =
                            alternativeDate == null ? proposal.day() : alternativeDate;
                    int slot = alternativeSlot == null ? proposal.slot() : alternativeSlot;
                    if (!isValidFutureClinicSlot(appointmentDate, slot)
                            || !slotMatchesType(proposal.appointmentType(), slot)) {
                        throw new InvalidAppointmentTransitionException();
                    }
                    acquireSlotLocks(connection, patientId, proposal.doctorId(), appointmentDate, slot);
                    if (hasConflictExcludingAppointment(connection, patientId, proposal.doctorId(),
                            appointmentDate, slot, appointmentId)) {
                        throw new AppointmentConflictException();
                    }
                    try (PreparedStatement update =
                                 connection.prepareStatement(CONFIRM_PROPOSAL_SQL)) {
                        update.setObject(1, appointmentDate);
                        update.setInt(2, slot);
                        update.setLong(3, appointmentId);
                        update.setLong(4, patientId);
                        requireOneUpdated(update.executeUpdate());
                    }
                } else {
                    try (PreparedStatement update =
                                 connection.prepareStatement(DECLINE_PROPOSAL_SQL)) {
                        update.setLong(1, appointmentId);
                        update.setLong(2, patientId);
                        requireOneUpdated(update.executeUpdate());
                    }
                }
                notifyAppointmentStatus(connection, appointmentId,
                        approved ? "CONFIRMED" : "DECLINED");
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private static void requireActiveDoctor(Connection connection, long doctorId)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(ACTIVE_DOCTOR_SQL)) {
            query.setLong(1, doctorId);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    throw new AppointmentNotFoundException();
                }
            }
        }
    }

    private static void requireEligibleProposalPatient(
            Connection connection, long doctorId, long patientId) throws SQLException {
        try (PreparedStatement query =
                     connection.prepareStatement(ELIGIBLE_PROPOSAL_PATIENT_SQL)) {
            query.setLong(1, doctorId);
            query.setLong(2, patientId);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    throw new AppointmentNotFoundException();
                }
            }
        }
    }

    private static void validateDoctorProposal(
            long doctorId, long patientId, String appointmentType,
            LocalDate day, int slot, String reason) {
        if (doctorId <= 0 || patientId <= 0 || appointmentType == null
                || !Set.of("FOLLOW_UP", "PROCEDURE").contains(appointmentType)
                || !isValidFutureClinicSlot(day, slot)
                || !slotMatchesType(appointmentType, slot)
                || reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("Doctor proposal details are invalid.");
        }
    }

    private record LockedProposal(
            long patientId,
            long doctorId,
            String appointmentType,
            LocalDate day,
            int slot,
            String status) {
    }

    private static void acquireSlotLocks(
            Connection connection, long patientId, long doctorId, LocalDate day, int slot)
            throws SQLException {
        List<String> lockKeys = new ArrayList<>(List.of(
                "doctor:" + doctorId + ":" + day + ":" + slot,
                "patient:" + patientId + ":" + day + ":" + slot));
        lockKeys.sort(String::compareTo);
        try (PreparedStatement lock = connection.prepareStatement(LOCK_SQL)) {
            for (String key : lockKeys) {
                lock.setString(1, key);
                try (ResultSet ignored = lock.executeQuery()) {
                    if (!ignored.next()) {
                        throw new SQLException("Could not reserve the requested appointment slot.");
                    }
                }
            }
        }
    }

    private static boolean hasConflict(
            Connection connection, long patientId, long doctorId, LocalDate day,
            int slot, Long excludedHoldId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ACTIVE_CONFLICT_SQL)) {
            statement.setObject(1, day);
            statement.setInt(2, slot);
            statement.setLong(3, doctorId);
            statement.setLong(4, patientId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return true;
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(TIME_OFF_CONFLICT_SQL)) {
            statement.setLong(1, doctorId);
            statement.setObject(2, day);
            statement.setInt(3, slot);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return true;
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(RESCHEDULE_CONFLICT_SQL)) {
            statement.setObject(1, day);
            statement.setInt(2, slot);
            statement.setLong(3, doctorId);
            statement.setLong(4, patientId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return true;
                }
            }
        }
        try (PreparedStatement statement =
                     connection.prepareStatement(ACTIVE_HOLD_CONFLICT_SQL)) {
            statement.setObject(1, day);
            statement.setInt(2, slot);
            statement.setLong(3, doctorId);
            statement.setLong(4, patientId);
            statement.setLong(5, excludedHoldId == null ? 0 : excludedHoldId);
            statement.setLong(6, excludedHoldId == null ? 0 : excludedHoldId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void expireSlotHolds(
            Connection connection, long patientId, long doctorId,
            LocalDate day, int slot) throws SQLException {
        expireSlotHolds(connection, patientId, doctorId, day, slot, null);
    }

    private static void expireSlotHolds(
            Connection connection, long patientId, long doctorId,
            LocalDate day, int slot, Long excludedHoldId) throws SQLException {
        String sql = excludedHoldId == null
                ? EXPIRE_SLOT_HOLDS_SQL : EXPIRE_SLOT_HOLDS_EXCEPT_HOLD_SQL;
        try (PreparedStatement expire = connection.prepareStatement(sql)) {
            expire.setObject(1, day);
            expire.setInt(2, slot);
            expire.setLong(3, doctorId);
            expire.setLong(4, patientId);
            if (excludedHoldId != null) {
                expire.setLong(5, excludedHoldId);
            }
            expire.executeUpdate();
        }
    }

    private static long insertConsultation(
            Connection connection, long patientId, long doctorId,
            LocalDate day, int slot, String reason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setLong(1, patientId);
            statement.setLong(2, doctorId);
            statement.setObject(3, day);
            statement.setInt(4, slot);
            statement.setString(5, reason);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Database did not return the created appointment.");
                }
                return requiredId(result, "appointment_id");
            }
        }
    }

    private static void validateConsultation(
            long patientId, long doctorId, LocalDate day, int slot, String reason) {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(reason, "reason");
        if (patientId <= 0 || doctorId <= 0 || slot < 0 || slot > 7 || slot % 2 != 0
                || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("Consultation request is invalid.");
        }
        LocalDate today = LocalDate.now();
        if (day.isBefore(today) || day.isAfter(today.plusDays(30))
                || day.getDayOfWeek() == DayOfWeek.SATURDAY
                || day.getDayOfWeek() == DayOfWeek.SUNDAY
                || !LocalDateTime.now().isBefore(slotStart(day, slot))) {
            throw new IllegalArgumentException("Choose an available clinic weekday and timeslot.");
        }
    }

    private static LocalDateTime slotStart(LocalDate day, int slot) {
        LocalTime[] starts = {
            LocalTime.of(8, 0), LocalTime.of(8, 45),
            LocalTime.of(10, 0), LocalTime.of(10, 45),
            LocalTime.of(12, 45), LocalTime.of(13, 30),
            LocalTime.of(14, 45), LocalTime.of(15, 30)
        };
        return day.atTime(starts[slot]);
    }

    private static Optional<BackendPatientProfile> readPatientProfile(
            Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PATIENT_PROFILE_SQL)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                long resolvedUserId = requiredId(result, "user_id");
                long rawProfileId = result.getLong("profile_id");
                if (result.wasNull()) {
                    return Optional.empty();
                }
                if (rawProfileId <= 0) {
                    throw new SQLException("Database returned an invalid patient profile ID.");
                }
                BackendPatientProfile profile = new BackendPatientProfile(
                        resolvedUserId,
                        text(result.getString("first_name")),
                        text(result.getString("last_name")),
                        text(result.getString("email")),
                        text(result.getString("phone")),
                        dateText(result.getObject("birth_date", LocalDate.class)),
                        text(result.getString("sex")),
                        text(result.getString("address")),
                        text(result.getString("blood_type")),
                        text(result.getString("allergies")),
                        text(result.getString("conditions")),
                        text(result.getString("medications")),
                        text(result.getString("medical_history")));
                if (result.next()) {
                    throw new AmbiguousPatientProfileException();
                }
                return Optional.of(profile);
            }
        }
    }

    private static long lockSingleProfile(Connection connection, long userId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_PROFILE_SQL)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new PatientProfileNotFoundException();
                }
                long profileId = requiredId(result, "profile_id");
                if (result.next()) {
                    throw new AmbiguousPatientProfileException();
                }
                return profileId;
            }
        }
    }

    private static String appointmentListSql(BackendRole role) {
        return switch (role) {
            case PATIENT -> PATIENT_APPOINTMENTS_SQL;
            case DOCTOR -> DOCTOR_APPOINTMENTS_SQL;
            case RECEPTIONIST -> RECEPTIONIST_APPOINTMENTS_SQL;
            case ADMIN -> null;
        };
    }

    private static List<BackendAppointment> mapAppointments(ResultSet result)
            throws SQLException {
        List<BackendAppointment> appointments = new ArrayList<>();
        while (result.next()) {
            appointments.add(mapAppointment(result));
        }
        return List.copyOf(appointments);
    }

    private static BackendAppointment mapAppointment(ResultSet result) throws SQLException {
        String type = result.getString("appointment_type");
        String status = result.getString("status");
        if (type == null || status == null
                || !Set.of("CONSULTATION", "FOLLOW_UP", "PROCEDURE").contains(type)
                || !Set.of("PENDING_RECEPTIONIST", "PENDING_PATIENT", "CONFIRMED",
                        "REJECTED", "DECLINED", "EXPIRED", "CANCELLED", "COMPLETED", "NO_SHOW")
                        .contains(status)) {
            throw new SQLException("Database returned an unsupported appointment type or status.");
        }
        LocalDate day = result.getObject("appointment_date", LocalDate.class);
        if (day == null) {
            throw new SQLException("Database returned an appointment without a date.");
        }
        return new BackendAppointment(
                requiredId(result, "appointment_id"),
                requiredId(result, "patient_id"),
                requiredId(result, "doctor_id"),
                type,
                day,
                requiredSlot(result, "slot_number"),
                status,
                text(result.getString("reason")),
                requiredText(result, "patient_first_name"),
                requiredText(result, "patient_last_name"),
                requiredText(result, "doctor_first_name"),
                requiredText(result, "doctor_last_name"),
                requiredText(result, "specialty"));
    }

    private static BackendTimeOffRequest mapTimeOff(ResultSet result) throws SQLException {
        String status = result.getString("status");
        if (!Set.of("pending", "approved", "rejected").contains(status)) {
            throw new SQLException("Database returned an unsupported time-off status.");
        }
        return new BackendTimeOffRequest(
                requiredId(result, "time_off_id"),
                requiredId(result, "doctor_id"),
                requiredDate(result, "request_date"),
                requiredSlot(result, "slot_number"),
                status,
                text(result.getString("reason")));
    }

    private static BackendAppointmentChangeRequest mapChangeRequest(ResultSet result)
            throws SQLException {
        String kind = result.getString("kind");
        String status = result.getString("status");
        if (!Set.of("cancellation", "reschedule").contains(kind)
                || !"pending".equals(status)) {
            throw new SQLException("Database returned an unsupported appointment-change request.");
        }
        Object rawSlot = result.getObject("proposed_slot");
        Integer proposedSlot = rawSlot == null ? null : ((Number) rawSlot).intValue();
        return new BackendAppointmentChangeRequest(
                requiredId(result, "request_id"),
                requiredId(result, "appointment_id"),
                requiredId(result, "requester_id"),
                kind,
                text(result.getString("details")),
                status,
                result.getObject("proposed_date", LocalDate.class),
                proposedSlot,
                requiredDate(result, "appointment_date"),
                requiredSlot(result, "appointment_slot"),
                result.getString("appointment_type"),
                requiredText(result, "requester_first_name") + " "
                        + requiredText(result, "requester_last_name"),
                requiredText(result, "patient_first_name") + " "
                        + requiredText(result, "patient_last_name"),
                "Dr. " + requiredText(result, "doctor_first_name") + " "
                        + requiredText(result, "doctor_last_name"));
    }

    private static LockedChangeAppointment readLockedChangeAppointment(ResultSet result)
            throws SQLException {
        String type = result.getString("appointment_type");
        if (!Set.of("CONSULTATION", "FOLLOW_UP", "PROCEDURE").contains(type)) {
            throw new SQLException("Database returned an unsupported appointment type.");
        }
        return new LockedChangeAppointment(
                requiredId(result, "appointment_id"),
                requiredId(result, "patient_id"),
                requiredId(result, "doctor_id"),
                type,
                requiredDate(result, "appointment_date"),
                requiredSlot(result, "slot_number"),
                result.getString("status"));
    }

    private static void validateFutureSlot(LocalDate day, int slot, String details) {
        if (!isValidFutureClinicSlot(day, slot) || details == null || details.isBlank()
                || details.length() > 2000) {
            throw new IllegalArgumentException("Choose a valid future clinic timeslot and explanation.");
        }
    }

    private static boolean isValidFutureClinicSlot(LocalDate day, int slot) {
        return day != null && slot >= 0 && slot <= 7 && !day.isBefore(LocalDate.now())
                && !day.isAfter(LocalDate.now().plusDays(30))
                && day.getDayOfWeek() != DayOfWeek.SATURDAY
                && day.getDayOfWeek() != DayOfWeek.SUNDAY
                && LocalDateTime.now().isBefore(slotStart(day, slot));
    }

    private static boolean slotMatchesType(String appointmentType, Integer slot) {
        return slot != null && slot >= 0 && slot <= 7
                && ((slot % 2 == 0) == !"PROCEDURE".equals(appointmentType));
    }

    private static void acquireDoctorSlotLock(
            Connection connection, long doctorId, LocalDate day, int slot) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement(LOCK_DOCTOR_SLOT_SQL)) {
            lock.setString(1, "doctor:" + doctorId + ":" + day + ":" + slot);
            try (ResultSet result = lock.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Database did not acquire the doctor slot lock.");
                }
            }
        }
    }

    private static boolean hasTimeOffConflict(
            Connection connection, long doctorId, LocalDate day, int slot) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(TIME_OFF_SLOT_CONFLICT_SQL)) {
            query.setLong(1, doctorId);
            query.setObject(2, day);
            query.setInt(3, slot);
            query.setLong(4, doctorId);
            query.setObject(5, day);
            query.setInt(6, slot);
            query.setLong(7, doctorId);
            query.setObject(8, day);
            query.setInt(9, slot);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    return true;
                }
            }
        }
        return hasActiveHoldConflict(connection, 0, doctorId, day, slot, null);
    }

    private static boolean hasActiveHoldConflict(
            Connection connection, long patientId, long doctorId,
            LocalDate day, int slot, Long excludedHoldId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(ACTIVE_HOLD_CONFLICT_SQL)) {
            query.setObject(1, day);
            query.setInt(2, slot);
            query.setLong(3, doctorId);
            query.setLong(4, patientId);
            query.setLong(5, excludedHoldId == null ? 0 : excludedHoldId);
            query.setLong(6, excludedHoldId == null ? 0 : excludedHoldId);
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean hasActiveAppointmentOrReschedule(
            Connection connection, long doctorId, LocalDate day, int slot) throws SQLException {
        expireSlotHolds(connection, 0, doctorId, day, slot);
        try (PreparedStatement query =
                     connection.prepareStatement(ACTIVE_APPOINTMENT_OR_RESCHEDULE_SQL)) {
            query.setLong(1, doctorId);
            query.setObject(2, day);
            query.setInt(3, slot);
            query.setLong(4, doctorId);
            query.setObject(5, day);
            query.setInt(6, slot);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    return true;
                }
            }
        }
        return hasActiveHoldConflict(connection, 0, doctorId, day, slot, null);
    }

    private static boolean hasConflictExcludingAppointment(
            Connection connection, long patientId, long doctorId, LocalDate day,
            int slot, long appointmentId) throws SQLException {
        expireSlotHolds(connection, patientId, doctorId, day, slot);
        try (PreparedStatement query = connection.prepareStatement(CHANGE_SLOT_CONFLICT_SQL)) {
            query.setObject(1, day);
            query.setInt(2, slot);
            query.setLong(3, appointmentId);
            query.setLong(4, doctorId);
            query.setLong(5, patientId);
            query.setLong(6, doctorId);
            query.setObject(7, day);
            query.setInt(8, slot);
            query.setObject(9, day);
            query.setInt(10, slot);
            query.setLong(11, appointmentId);
            query.setLong(12, doctorId);
            query.setLong(13, patientId);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    return true;
                }
            }
        }
        return hasActiveHoldConflict(connection, patientId, doctorId, day, slot, null);
    }

    private static long doctorUserId(Connection connection, long doctorId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(DOCTOR_USER_SQL)) {
            query.setLong(1, doctorId);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    throw new AppointmentNotFoundException();
                }
                long userId = requiredId(result, "user_id");
                if (result.next()) {
                    throw new SQLException("Database returned duplicate doctor user records.");
                }
                return userId;
            }
        }
    }

    private static void insertNotification(
            Connection connection, long recipientId, String message) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(INSERT_NOTIFICATION_SQL)) {
            insert.setLong(1, recipientId);
            insert.setString(2, message);
            requireOneUpdated(insert.executeUpdate());
        }
    }

    private static void insertNotificationsForRole(
            Connection connection, String role, String message) throws SQLException {
        try (PreparedStatement insert =
                     connection.prepareStatement(INSERT_NOTIFICATIONS_FOR_ROLE_SQL)) {
            insert.setString(1, message);
            insert.setString(2, role);
            insert.executeUpdate();
        }
    }

    private <T> T inTransaction(SqlWork<T> operation) throws SQLException {
        try (Connection connection = connectionProvider.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = operation.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private void updateAppointmentStatus(
            String lockSql,
            long ownerId,
            long appointmentId,
            String newStatus,
            String expectedStatus,
            String doctorFinishStatus) throws SQLException {
        if (appointmentId <= 0) {
            throw new IllegalArgumentException("Appointment ID must be positive.");
        }
        try (Connection connection = connectionProvider.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                LockedAppointment appointment;
                try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
                    lock.setLong(1, appointmentId);
                    if (ownerId > 0) {
                        lock.setLong(2, ownerId);
                    }
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next()) {
                            throw new AppointmentNotFoundException();
                        }
                        appointment = new LockedAppointment(
                                result.getString("status"),
                                result.getObject("appointment_date", LocalDate.class),
                                requiredSlot(result, "slot_number"));
                    }
                }
                if (!expectedStatus.equals(appointment.status())) {
                    throw new InvalidAppointmentTransitionException();
                }
                if (doctorFinishStatus != null
                        && LocalDateTime.now().isBefore(
                                slotEnd(appointment.day(), appointment.slot()))) {
                    throw new InvalidAppointmentTransitionException();
                }
                try (PreparedStatement update =
                             connection.prepareStatement(UPDATE_APPOINTMENT_STATUS_SQL)) {
                    update.setString(1, newStatus);
                    update.setLong(2, appointmentId);
                    update.setString(3, expectedStatus);
                    requireOneUpdated(update.executeUpdate());
                }
                notifyAppointmentStatus(connection, appointmentId, newStatus);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private static LocalDateTime slotEnd(LocalDate day, int slot) {
        LocalTime[] ends = {
            LocalTime.of(8, 30), LocalTime.of(9, 45),
            LocalTime.of(10, 30), LocalTime.of(11, 45),
            LocalTime.of(13, 15), LocalTime.of(14, 30),
            LocalTime.of(15, 15), LocalTime.of(16, 30)
        };
        return day.atTime(ends[slot]);
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static void requireOneUpdated(int rowCount) throws SQLException {
        if (rowCount != 1) {
            throw new SQLException("Database update did not affect exactly one record.");
        }
    }

    private static String requiredText(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) {
            throw new SQLException("Database returned incomplete appointment details.");
        }
        return value;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String dateText(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private record LockedAppointment(String status, LocalDate day, int slot) {
    }

    private record LockedChangeAppointment(
            long appointmentId,
            long patientId,
            long doctorId,
            String appointmentType,
            LocalDate day,
            int slot,
            String status) {
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }

    private static void readAppointments(
            Connection connection, long patientId, long doctorId,
            LocalDate day, AvailabilityState[] states) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(APPOINTMENTS_SQL)) {
            statement.setObject(1, day);
            statement.setLong(2, doctorId);
            statement.setLong(3, patientId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long rowPatientId = requiredId(result, "patient_id");
                    long rowDoctorId = requiredId(result, "doctor_id");
                    int slot = requiredSlot(result, "slot_number");
                    if (rowPatientId == patientId) {
                        states[slot] = AvailabilityState.PATIENT_CONFLICT;
                    } else if (rowDoctorId == doctorId) {
                        states[slot] = AvailabilityState.APPOINTMENT;
                    } else {
                        throw new SQLException("Availability query returned an out-of-scope row.");
                    }
                }
            }
        }
    }

    private static void readApprovedTimeOff(
            Connection connection, long doctorId, LocalDate day, AvailabilityState[] states)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TIME_OFF_SQL)) {
            statement.setLong(1, doctorId);
            statement.setObject(2, day);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int slot = requiredSlot(result, "slot_number");
                    if (states[slot] == AvailabilityState.AVAILABLE) {
                        states[slot] = AvailabilityState.APPROVED_TIME_OFF;
                    }
                }
            }
        }
    }

    private static void readDoctorAppointments(
            Connection connection, long doctorId, LocalDate day, AvailabilityState[] states)
            throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(DOCTOR_SCHEDULE_APPOINTMENTS_SQL)) {
            statement.setObject(1, day);
            statement.setLong(2, doctorId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int slot = requiredSlot(result, "slot_number");
                    states[slot] = AvailabilityState.APPOINTMENT;
                }
            }
        }
    }

    private static void readDoctorRescheduleHolds(
            Connection connection, long doctorId, LocalDate day, AvailabilityState[] states)
            throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(DOCTOR_SCHEDULE_RESCHEDULE_SQL)) {
            statement.setObject(1, day);
            statement.setLong(2, doctorId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int slot = requiredSlot(result, "proposed_slot");
                    if (states[slot] == AvailabilityState.AVAILABLE) {
                        states[slot] = AvailabilityState.RESCHEDULE_HOLD;
                    }
                }
            }
        }
    }

    private static void readPendingRescheduleHolds(
            Connection connection, long patientId, long doctorId,
            LocalDate day, AvailabilityState[] states) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RESCHEDULE_SQL)) {
            statement.setObject(1, day);
            statement.setLong(2, doctorId);
            statement.setLong(3, patientId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long rowPatientId = requiredId(result, "patient_id");
                    long rowDoctorId = requiredId(result, "doctor_id");
                    int slot = requiredSlot(result, "proposed_slot");
                    if (rowPatientId == patientId) {
                        states[slot] = AvailabilityState.PATIENT_CONFLICT;
                    } else if (rowDoctorId == doctorId
                            && states[slot] == AvailabilityState.AVAILABLE) {
                        states[slot] = AvailabilityState.RESCHEDULE_HOLD;
                    } else if (rowDoctorId != doctorId) {
                        throw new SQLException("Availability query returned an out-of-scope hold.");
                    }
                }
            }
        }
    }

    private static void readActiveHolds(
            Connection connection, long patientId, long doctorId,
            LocalDate day, AvailabilityState[] states) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ACTIVE_HOLDS_SQL)) {
            statement.setLong(1, doctorId);
            statement.setLong(2, patientId);
            statement.setObject(3, day);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long rowPatientId = requiredId(result, "patient_id");
                    int slot = requiredSlot(result, "slot_number");
                    if (rowPatientId == patientId) {
                        states[slot] = AvailabilityState.PATIENT_CONFLICT;
                    } else if (states[slot] == AvailabilityState.AVAILABLE) {
                        states[slot] = AvailabilityState.ACTIVE_HOLD;
                    }
                }
            }
        }
    }

    private static void readDoctorActiveHolds(
            Connection connection, long doctorId, LocalDate day,
            AvailabilityState[] states) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ACTIVE_HOLDS_SQL)) {
            statement.setLong(1, doctorId);
            statement.setLong(2, 0);
            statement.setObject(3, day);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int slot = requiredSlot(result, "slot_number");
                    if (states[slot] == AvailabilityState.AVAILABLE) {
                        states[slot] = AvailabilityState.ACTIVE_HOLD;
                    }
                }
            }
        }
    }

    private static BackendRole parseRole(String role) throws SQLException {
        try {
            return BackendRole.valueOf(role.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new SQLException("Database user has an unsupported application role.", exception);
        }
    }

    private static long requiredId(ResultSet result, String column) throws SQLException {
        long id = result.getLong(column);
        if (result.wasNull() || id <= 0) {
            throw new SQLException("Database returned an invalid identifier.");
        }
        return id;
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static int requiredSlot(ResultSet result, String column) throws SQLException {
        int slot = result.getInt(column);
        if (result.wasNull() || slot < 0 || slot > 7) {
            throw new SQLException("Database returned an invalid appointment slot.");
        }
        return slot;
    }

    private static LocalDate requiredDate(ResultSet result, String column) throws SQLException {
        LocalDate day = result.getObject(column, LocalDate.class);
        if (day == null) {
            throw new SQLException("Database returned a record without a date.");
        }
        return day;
    }

    private enum AvailabilityState {
        AVAILABLE,
        APPOINTMENT,
        PATIENT_CONFLICT,
        APPROVED_TIME_OFF,
        RESCHEDULE_HOLD,
        ACTIVE_HOLD
    }

    @FunctionalInterface
    public interface AppointmentRepositoryConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    public static final class AppointmentConflictException extends RuntimeException {
    }
}
