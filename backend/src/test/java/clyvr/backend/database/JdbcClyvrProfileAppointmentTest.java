package clyvr.backend.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import clyvr.backend.identity.BackendRole;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class JdbcClyvrProfileAppointmentTest {

    @Test
    void mapsPatientProfileWithoutSelectingAuthenticationSecrets() throws Exception {
        JdbcFixture fixture = new JdbcFixture();
        fixture.connectionDefaults();
        ResultSet profile = profileResult(402, 502L, false);
        fixture.add(JdbcClyvrDataRepository.PATIENT_PROFILE_SQL, resultStatement(profile));

        Optional<BackendPatientProfile> result =
                new JdbcClyvrDataRepository(() -> fixture.connection)
                        .findPatientProfile(402);

        assertEquals("Ada", result.orElseThrow().firstName());
        assertEquals("1990-01-01", result.orElseThrow().birthDate());
        org.junit.jupiter.api.Assertions.assertFalse(
                JdbcClyvrDataRepository.PATIENT_PROFILE_SQL.contains("password_hash"));
        verify(fixture.connection).prepareStatement(JdbcClyvrDataRepository.PATIENT_PROFILE_SQL);
        verify(fixture.statement(JdbcClyvrDataRepository.PATIENT_PROFILE_SQL)).setLong(1, 402);
    }

    @Test
    void reportsMissingAndDuplicateProfilesWithoutGuessing() throws Exception {
        JdbcFixture missing = new JdbcFixture();
        missing.connectionDefaults();
        missing.add(JdbcClyvrDataRepository.PATIENT_PROFILE_SQL,
                resultStatement(profileResult(402, null, false)));
        assertEquals(Optional.empty(), new JdbcClyvrDataRepository(() -> missing.connection)
                .findPatientProfile(402));

        JdbcFixture duplicate = new JdbcFixture();
        duplicate.connectionDefaults();
        duplicate.add(JdbcClyvrDataRepository.PATIENT_PROFILE_SQL,
                resultStatement(profileResult(402, 502L, true)));
        assertThrows(AmbiguousPatientProfileException.class,
                () -> new JdbcClyvrDataRepository(() -> duplicate.connection)
                        .findPatientProfile(402));
    }

    @Test
    void updatesUserAndSinglePatientProfileAtomically() throws Exception {
        JdbcFixture fixture = new JdbcFixture();
        fixture.connectionDefaults();
        fixture.add(JdbcClyvrDataRepository.LOCK_PATIENT_SQL,
                resultStatement(oneRowResult()));
        ResultSet profileLock = oneRowResult();
        when(profileLock.getLong("profile_id")).thenReturn(502L);
        when(profileLock.wasNull()).thenReturn(false);
        fixture.add(JdbcClyvrDataRepository.LOCK_PROFILE_SQL, resultStatement(profileLock));
        fixture.add(JdbcClyvrDataRepository.UPDATE_USER_PROFILE_SQL, updateStatement(1));
        fixture.add(JdbcClyvrDataRepository.UPDATE_PATIENT_PROFILE_SQL, updateStatement(1));
        ResultSet updatedProfile = profileResult(402, 502L, false);
        when(updatedProfile.getString("first_name")).thenReturn("Taylor");
        fixture.add(JdbcClyvrDataRepository.PATIENT_PROFILE_SQL,
                resultStatement(updatedProfile));
        PatientProfileUpdate update = update();

        BackendPatientProfile saved =
                new JdbcClyvrDataRepository(() -> fixture.connection)
                        .updatePatientProfile(402, update);

        assertEquals(402, saved.userId());
        assertEquals("Taylor", saved.firstName());
        verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
        verify(fixture.statement(JdbcClyvrDataRepository.UPDATE_USER_PROFILE_SQL))
                .setLong(4, 402);
        verify(fixture.statement(JdbcClyvrDataRepository.UPDATE_PATIENT_PROFILE_SQL))
                .setLong(10, 402);
    }

    @Test
    void refusesToUpdateDuplicateProfilesAndRollsBack() throws Exception {
        JdbcFixture fixture = new JdbcFixture();
        fixture.connectionDefaults();
        fixture.add(JdbcClyvrDataRepository.LOCK_PATIENT_SQL,
                resultStatement(oneRowResult()));
        ResultSet duplicates = oneRowResult();
        when(duplicates.next()).thenReturn(true, true);
        when(duplicates.getLong("profile_id")).thenReturn(502L, 503L);
        when(duplicates.wasNull()).thenReturn(false, false);
        fixture.add(JdbcClyvrDataRepository.LOCK_PROFILE_SQL, resultStatement(duplicates));

        assertThrows(AmbiguousPatientProfileException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .updatePatientProfile(402, update()));

        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).commit();
        verify(fixture.connection, never())
                .prepareStatement(JdbcClyvrDataRepository.UPDATE_USER_PROFILE_SQL);
    }

    @Test
    void profileUpdateFailureRollsBackBothUserAndProfileChanges() throws Exception {
        JdbcFixture fixture = new JdbcFixture();
        fixture.connectionDefaults();
        fixture.add(JdbcClyvrDataRepository.LOCK_PATIENT_SQL,
                resultStatement(oneRowResult()));
        ResultSet profileLock = oneRowResult();
        when(profileLock.getLong("profile_id")).thenReturn(502L);
        when(profileLock.wasNull()).thenReturn(false);
        fixture.add(JdbcClyvrDataRepository.LOCK_PROFILE_SQL, resultStatement(profileLock));
        fixture.add(JdbcClyvrDataRepository.UPDATE_USER_PROFILE_SQL, updateStatement(1));
        PreparedStatement failedProfileUpdate = updateStatement(0);
        when(failedProfileUpdate.executeUpdate()).thenThrow(new SQLException("synthetic failure"));
        fixture.add(JdbcClyvrDataRepository.UPDATE_PATIENT_PROFILE_SQL, failedProfileUpdate);

        assertThrows(SQLException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .updatePatientProfile(402, update()));

        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).commit();
    }

    @Test
    void queriesAppointmentsWithDatabaseIdentityScope() throws Exception {
        JdbcFixture fixture = new JdbcFixture();
        fixture.connectionDefaults();
        fixture.add(JdbcClyvrDataRepository.PATIENT_APPOINTMENTS_SQL,
                resultStatement(emptyResult()));
        fixture.add(JdbcClyvrDataRepository.DOCTOR_APPOINTMENTS_SQL,
                resultStatement(emptyResult()));
        JdbcClyvrDataRepository repository =
                new JdbcClyvrDataRepository(() -> fixture.connection);

        assertEquals(List.of(), repository.findAppointments(BackendRole.PATIENT, 402, null));
        verify(fixture.statement(JdbcClyvrDataRepository.PATIENT_APPOINTMENTS_SQL))
                .setLong(1, 402);
        assertEquals(List.of(), repository.findAppointments(BackendRole.DOCTOR, 403, 701L));
        verify(fixture.statement(JdbcClyvrDataRepository.DOCTOR_APPOINTMENTS_SQL))
                .setLong(1, 701);
        assertThrows(IllegalArgumentException.class,
                () -> repository.findAppointments(BackendRole.ADMIN, 408, null));
    }

    @Test
    void receptionistApprovalUsesLockedPendingStateAndCommits() throws Exception {
        JdbcFixture fixture = statusFixture("PENDING_RECEPTIONIST",
                LocalDate.now().plusDays(1), 0, 1);
        JdbcClyvrDataRepository repository =
                new JdbcClyvrDataRepository(() -> fixture.connection);

        repository.decidePendingConsultation(9001, true);

        verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
        PreparedStatement update = fixture.statement(
                JdbcClyvrDataRepository.UPDATE_APPOINTMENT_STATUS_SQL);
        verify(update).setString(1, "CONFIRMED");
        verify(update).setLong(2, 9001);
        verify(update).setString(3, "PENDING_RECEPTIONIST");
    }

    @Test
    void receptionistRejectionPersistsOnlyTheExistingStatusValue() throws Exception {
        JdbcFixture fixture = statusFixture("PENDING_RECEPTIONIST",
                LocalDate.now().plusDays(1), 0, 1);

        new JdbcClyvrDataRepository(() -> fixture.connection)
                .decidePendingConsultation(9001, false);

        verify(fixture.statement(JdbcClyvrDataRepository.UPDATE_APPOINTMENT_STATUS_SQL))
                .setString(1, "REJECTED");
        verify(fixture.statement(JdbcClyvrDataRepository.UPDATE_APPOINTMENT_STATUS_SQL))
                .setString(3, "PENDING_RECEPTIONIST");
        PreparedStatement notifications =
                fixture.statement(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL);
        ArgumentCaptor<Long> recipients = ArgumentCaptor.forClass(Long.class);
        verify(notifications, times(2)).setLong(org.mockito.ArgumentMatchers.eq(1),
                recipients.capture());
        assertEquals(List.of(402L, 403L), recipients.getAllValues());
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(notifications, times(2)).setString(org.mockito.ArgumentMatchers.eq(2),
                messages.capture());
        org.junit.jupiter.api.Assertions.assertTrue(messages.getAllValues().stream()
                .allMatch(message -> message.contains("REJECTED")));
        verify(notifications, times(2)).executeUpdate();
        verify(fixture.connection).commit();
    }

    @Test
    void patientCancellationAndDoctorCompletionPersistAllowedStatuses() throws Exception {
        JdbcFixture patient = statusFixture("CONFIRMED", LocalDate.now().plusDays(1), 0, 1);
        new JdbcClyvrDataRepository(() -> patient.connection)
                .cancelPatientAppointment(402, 9001);
        verify(patient.statement(JdbcClyvrDataRepository.UPDATE_APPOINTMENT_STATUS_SQL))
                .setString(1, "CANCELLED");
        verify(patient.connection).commit();

        JdbcFixture doctor = statusFixture("CONFIRMED", LocalDate.now().minusDays(1), 0, 1);
        new JdbcClyvrDataRepository(() -> doctor.connection)
                .finishDoctorAppointment(701, 9001, "NO_SHOW");
        verify(doctor.statement(JdbcClyvrDataRepository.UPDATE_APPOINTMENT_STATUS_SQL))
                .setString(1, "NO_SHOW");
        verify(doctor.connection).commit();
    }

    @Test
    void appointmentDetailsQueryIncludesRoleOwnershipInItsPredicate() throws Exception {
        JdbcFixture fixture = new JdbcFixture();
        PreparedStatement statement = resultStatement(emptyResult());
        doReturn(statement).when(fixture.connection).prepareStatement(anyString());
        JdbcClyvrDataRepository repository =
                new JdbcClyvrDataRepository(() -> fixture.connection);

        assertEquals(Optional.empty(),
                repository.findAppointment(BackendRole.PATIENT, 402, null, 9001));
        org.mockito.ArgumentCaptor<String> sql =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(fixture.connection).prepareStatement(sql.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                sql.getValue().contains("WHERE a.patient_id = ? AND a.appointment_id = ?"));
        verify(statement).setLong(1, 402);
        verify(statement).setLong(2, 9001);
    }

    @Test
    void rejectsInvalidStatusTransitionsAndOtherPatientsAppointments() throws Exception {
        JdbcFixture invalidStatus = statusFixture("CANCELLED",
                LocalDate.now().plusDays(1), 0, 1);
        assertThrows(InvalidAppointmentTransitionException.class,
                () -> new JdbcClyvrDataRepository(() -> invalidStatus.connection)
                        .decidePendingConsultation(9001, true));
        verify(invalidStatus.connection).rollback();
        verify(invalidStatus.connection, never()).commit();

        JdbcFixture otherOwner = statusFixture(null,
                LocalDate.now().plusDays(1), 0, 0);
        assertThrows(AppointmentNotFoundException.class,
                () -> new JdbcClyvrDataRepository(() -> otherOwner.connection)
                        .cancelPatientAppointment(999, 9001));
        verify(otherOwner.statement(
                JdbcClyvrDataRepository.LOCK_APPOINTMENT_BY_PATIENT_SQL))
                .setLong(2, 999);
        verify(otherOwner.connection).rollback();
    }

    @Test
    void databaseUpdateFailureRollsBackAndIsNotReportedAsSuccess() throws Exception {
        JdbcFixture fixture = statusFixture("PENDING_RECEPTIONIST",
                LocalDate.now().plusDays(1), 0, 1);
        when(fixture.statement(JdbcClyvrDataRepository.UPDATE_APPOINTMENT_STATUS_SQL)
                .executeUpdate()).thenThrow(new SQLException("synthetic failure"));

        assertThrows(SQLException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .decidePendingConsultation(9001, false));
        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).commit();
    }

    @Test
    void doctorCannotFinishBeforeTheAppointmentEnd() throws Exception {
        JdbcFixture fixture = statusFixture("CONFIRMED",
                LocalDate.now().plusDays(1), 0, 1);
        assertThrows(InvalidAppointmentTransitionException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .finishDoctorAppointment(701, 9001, "COMPLETED"));
        verify(fixture.statement(JdbcClyvrDataRepository.LOCK_APPOINTMENT_BY_DOCTOR_SQL))
                .setLong(2, 701);
        verify(fixture.connection).rollback();
    }

    private static PatientProfileUpdate update() {
        return new PatientProfileUpdate("Taylor", "Patient", "555-0101",
                "1990-01-01", "Other", "New address", "Unknown",
                "Tree nuts", "", "", "");
    }

    private static JdbcFixture statusFixture(
            String status, LocalDate day, int slot, int matchingRows) throws Exception {
        JdbcFixture fixture = new JdbcFixture();
        fixture.connectionDefaults();
        ResultSet locked = oneRowResult();
        when(locked.next()).thenReturn(matchingRows > 0, false);
        when(locked.getString("status")).thenReturn(status);
        when(locked.getObject("appointment_date", LocalDate.class)).thenReturn(day);
        when(locked.getInt("slot_number")).thenReturn(slot);
        when(locked.wasNull()).thenReturn(false);
        fixture.add(JdbcClyvrDataRepository.LOCK_PENDING_CONSULTATION_SQL,
                resultStatement(locked));
        fixture.add(JdbcClyvrDataRepository.LOCK_APPOINTMENT_BY_PATIENT_SQL,
                resultStatement(locked));
        fixture.add(JdbcClyvrDataRepository.LOCK_APPOINTMENT_BY_DOCTOR_SQL,
                resultStatement(locked));
        fixture.add(JdbcClyvrDataRepository.UPDATE_APPOINTMENT_STATUS_SQL, updateStatement(1));
        ResultSet recipients = oneRowResult();
        when(recipients.getLong("patient_id")).thenReturn(402L);
        when(recipients.getLong("doctor_user_id")).thenReturn(403L);
        when(recipients.wasNull()).thenReturn(false);
        fixture.add(JdbcClyvrDataRepository.APPOINTMENT_RECIPIENTS_SQL,
                resultStatement(recipients));
        fixture.add(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL, updateStatement(1));
        return fixture;
    }

    private static ResultSet profileResult(long userId, Long profileId, boolean duplicate)
            throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, duplicate);
        when(result.getLong("user_id")).thenReturn(userId);
        when(result.getLong("profile_id")).thenReturn(profileId == null ? 0 : profileId);
        when(result.wasNull()).thenReturn(false, profileId == null);
        when(result.getString("first_name")).thenReturn("Ada");
        when(result.getString("last_name")).thenReturn("Patient");
        when(result.getString("email")).thenReturn("ada@example.org");
        when(result.getString("phone")).thenReturn("555-0100");
        when(result.getObject("birth_date", LocalDate.class))
                .thenReturn(LocalDate.of(1990, 1, 1));
        when(result.getString("sex")).thenReturn("Other");
        when(result.getString("address")).thenReturn("Test address");
        when(result.getString("blood_type")).thenReturn("Unknown");
        when(result.getString("allergies")).thenReturn("");
        when(result.getString("conditions")).thenReturn("");
        when(result.getString("medications")).thenReturn("");
        when(result.getString("medical_history")).thenReturn("");
        return result;
    }

    private static ResultSet oneRowResult() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        return result;
    }

    private static ResultSet emptyResult() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(false);
        return result;
    }

    private static PreparedStatement resultStatement(ResultSet result) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeQuery()).thenReturn(result);
        return statement;
    }

    private static PreparedStatement updateStatement(int rows) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeUpdate()).thenReturn(rows);
        return statement;
    }

    private static final class JdbcFixture {
        private final Connection connection = mock(Connection.class);
        private final java.util.Map<String, PreparedStatement> statements =
                new java.util.HashMap<>();

        private void connectionDefaults() throws Exception {
            when(connection.getAutoCommit()).thenReturn(true);
            doAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                PreparedStatement statement = statements.get(sql);
                if (statement == null) {
                    throw new AssertionError("Unexpected SQL: " + sql);
                }
                return statement;
            }).when(connection).prepareStatement(anyString());
        }

        private void add(String sql, PreparedStatement statement) {
            statements.put(sql, statement);
        }

        private PreparedStatement statement(String sql) {
            return statements.get(sql);
        }
    }
}
