package clyvr.backend.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

class JdbcClyvrDataRepositoryTest {

    @Test
    void insertsOnlyAfterAdvisoryLocksAndConflictChecksInSameTransaction() throws Exception {
        TestJdbc jdbc = new TestJdbc(false);
        JdbcClyvrDataRepository repository = new JdbcClyvrDataRepository(() -> jdbc.connection);

        long id = repository.insertConsultation(
                402, 701, nextWeekday(), 0, "Synthetic test consultation");

        assertEquals(9003, id);
        verify(jdbc.connection).setAutoCommit(false);
        verify(jdbc.connection).commit();
        verify(jdbc.connection, never()).rollback();
        verify(jdbc.lockStatement, org.mockito.Mockito.times(2)).executeQuery();
        verify(jdbc.connection).prepareStatement(JdbcClyvrDataRepository.ACTIVE_CONFLICT_SQL);
        verify(jdbc.connection).prepareStatement(JdbcClyvrDataRepository.TIME_OFF_CONFLICT_SQL);
        verify(jdbc.connection).prepareStatement(JdbcClyvrDataRepository.RESCHEDULE_CONFLICT_SQL);
        verify(jdbc.connection).prepareStatement(JdbcClyvrDataRepository.ACTIVE_HOLD_CONFLICT_SQL);
        verify(jdbc.connection).prepareStatement(JdbcClyvrDataRepository.EXPIRE_SLOT_HOLDS_SQL);
        verify(jdbc.insertStatement).setString(5, "Synthetic test consultation");
    }

    @Test
    void rollsBackWhenAnotherAppointmentAlreadyUsesTheSlot() throws Exception {
        TestJdbc jdbc = new TestJdbc(true);
        JdbcClyvrDataRepository repository = new JdbcClyvrDataRepository(() -> jdbc.connection);

        assertThrows(JdbcClyvrDataRepository.AppointmentConflictException.class,
                () -> repository.insertConsultation(
                        402, 701, nextWeekday(), 0, "Synthetic conflict test"));

        verify(jdbc.connection).rollback();
        verify(jdbc.connection, never()).commit();
        verify(jdbc.connection, never()).prepareStatement(JdbcClyvrDataRepository.INSERT_SQL);
    }

    @Test
    void ordinaryBookingRejectsAnExistingActiveHold() throws Exception {
        TestJdbc jdbc = new TestJdbc(false, true);
        JdbcClyvrDataRepository repository = new JdbcClyvrDataRepository(() -> jdbc.connection);

        assertThrows(JdbcClyvrDataRepository.AppointmentConflictException.class,
                () -> repository.insertConsultation(
                        402, 701, nextWeekday(), 0, "Synthetic hold conflict"));

        verify(jdbc.connection).rollback();
        verify(jdbc.connection, never()).commit();
        verify(jdbc.connection, never()).prepareStatement(JdbcClyvrDataRepository.INSERT_SQL);
        verify(jdbc.activeHoldConflictStatement).setLong(4, 402);
    }

    @Test
    void rejectsInvalidConsultationBeforeOpeningDatabaseConnection() {
        JdbcClyvrDataRepository repository = new JdbcClyvrDataRepository(
                () -> { throw new AssertionError("Invalid input must not open a connection."); });

        assertThrows(IllegalArgumentException.class,
                () -> repository.insertConsultation(
                        402, 701, nextWeekday(), 1, "Odd slots are not consultations."));
    }

    @Test
    void readsDoctorAppointmentsTimeOffAndPendingRescheduleHolds() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement appointments = mock(PreparedStatement.class);
        PreparedStatement timeOff = mock(PreparedStatement.class);
        PreparedStatement reschedules = mock(PreparedStatement.class);
        PreparedStatement activeHolds = mock(PreparedStatement.class);
        ResultSet appointmentRows = rowsForSlot("slot_number", 2);
        ResultSet timeOffRows = rowsForSlot("slot_number", 4);
        ResultSet rescheduleRows = rowsForSlot("proposed_slot", 6);
        ResultSet emptyRows = mock(ResultSet.class);
        when(emptyRows.next()).thenReturn(false);
        when(connection.prepareStatement(eq(
                JdbcClyvrDataRepository.DOCTOR_SCHEDULE_APPOINTMENTS_SQL)))
                .thenReturn(appointments);
        when(connection.prepareStatement(eq(JdbcClyvrDataRepository.TIME_OFF_SQL)))
                .thenReturn(timeOff);
        when(connection.prepareStatement(eq(
                JdbcClyvrDataRepository.DOCTOR_SCHEDULE_RESCHEDULE_SQL)))
                .thenReturn(reschedules);
        when(connection.prepareStatement(eq(JdbcClyvrDataRepository.ACTIVE_HOLDS_SQL)))
                .thenReturn(activeHolds);
        when(appointments.executeQuery()).thenReturn(appointmentRows);
        when(timeOff.executeQuery()).thenReturn(timeOffRows);
        when(reschedules.executeQuery()).thenReturn(rescheduleRows);
        when(activeHolds.executeQuery()).thenReturn(emptyRows);

        LocalDate day = LocalDate.of(2026, 11, 9);
        BackendAvailability result =
                new JdbcClyvrDataRepository(() -> connection).findDoctorAvailability(701, day);

        assertEquals(701, result.doctorId());
        assertEquals("APPOINTMENT", result.slots().get(2).state());
        assertEquals("APPROVED_TIME_OFF", result.slots().get(4).state());
        assertEquals("RESCHEDULE_HOLD", result.slots().get(6).state());
        assertTrue(result.slots().stream()
                .filter(slot -> slot.slotNumber() != 2 && slot.slotNumber() != 4
                        && slot.slotNumber() != 6)
                .allMatch(slot -> "AVAILABLE".equals(slot.state())));
        verify(appointments).setObject(1, day);
        verify(appointments).setLong(2, 701);
        verify(timeOff).setLong(1, 701);
        verify(timeOff).setObject(2, day);
        verify(reschedules).setObject(1, day);
        verify(reschedules).setLong(2, 701);
        verify(activeHolds).setLong(1, 701);
        verify(activeHolds).setLong(2, 0);
    }

    @Test
    void rejectsInvalidDoctorScheduleBeforeOpeningConnection() {
        JdbcClyvrDataRepository repository = new JdbcClyvrDataRepository(
                () -> { throw new AssertionError("Invalid input must not open a connection."); });
        assertThrows(IllegalArgumentException.class,
                () -> repository.findDoctorAvailability(0, LocalDate.now()));
    }

    @Test
    void propagatesDoctorAvailabilityDatabaseFailures() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException failure = new SQLException("synthetic doctor schedule failure");
        when(connection.prepareStatement(
                JdbcClyvrDataRepository.DOCTOR_SCHEDULE_APPOINTMENTS_SQL))
                .thenThrow(failure);
        SQLException actual = assertThrows(SQLException.class,
                () -> new JdbcClyvrDataRepository(() -> connection)
                        .findDoctorAvailability(701, LocalDate.of(2026, 11, 9)));
        assertSame(failure, actual);
    }

    private static ResultSet rowsForSlot(String column, int slot) throws Exception {
        ResultSet rows = mock(ResultSet.class);
        when(rows.next()).thenReturn(true, false);
        when(rows.getInt(column)).thenReturn(slot);
        when(rows.wasNull()).thenReturn(false);
        return rows;
    }

    private static LocalDate nextWeekday() {
        LocalDate day = LocalDate.now().plusDays(1);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY
                || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    private static final class TestJdbc {
        private final Connection connection = mock(Connection.class);
        private final PreparedStatement lockStatement = mock(PreparedStatement.class);
        private final PreparedStatement activeConflictStatement = mock(PreparedStatement.class);
        private final PreparedStatement activeHoldConflictStatement = mock(PreparedStatement.class);
        private final PreparedStatement expireHoldsStatement = mock(PreparedStatement.class);
        private final PreparedStatement timeOffStatement = mock(PreparedStatement.class);
        private final PreparedStatement rescheduleStatement = mock(PreparedStatement.class);
        private final PreparedStatement insertStatement = mock(PreparedStatement.class);
        private final PreparedStatement notificationStatement = mock(PreparedStatement.class);
        private final PreparedStatement roleNotificationsStatement = mock(PreparedStatement.class);
        private final PreparedStatement activeDoctorStatement = mock(PreparedStatement.class);
        private final List<PreparedStatement> statements = new ArrayList<>();

        private TestJdbc(boolean conflict) throws Exception {
            this(conflict, false);
        }

        private TestJdbc(boolean conflict, boolean activeHoldConflict) throws Exception {
            ResultSet lockResult = mock(ResultSet.class);
            ResultSet emptyResult = mock(ResultSet.class);
            ResultSet conflictingResult = mock(ResultSet.class);
            ResultSet insertedResult = mock(ResultSet.class);
            ResultSet activeDoctorResult = mock(ResultSet.class);
            ResultSet activeHoldRows = mock(ResultSet.class);
            when(lockResult.next()).thenReturn(true, true);
            when(emptyResult.next()).thenReturn(false);
            when(conflictingResult.next()).thenReturn(true);
            when(insertedResult.next()).thenReturn(true);
            when(insertedResult.getLong("appointment_id")).thenReturn(9003L);
            when(insertedResult.wasNull()).thenReturn(false);
            when(activeDoctorResult.next()).thenReturn(true);
            when(activeHoldRows.next()).thenReturn(activeHoldConflict);
            when(expireHoldsStatement.executeUpdate()).thenReturn(0);
            when(connection.getAutoCommit()).thenReturn(true);

            when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                PreparedStatement statement;
                if (sql.equals(JdbcClyvrDataRepository.LOCK_SQL)) {
                    statement = lockStatement;
                    when(statement.executeQuery()).thenReturn(lockResult);
                } else if (sql.equals(JdbcClyvrDataRepository.ACTIVE_CONFLICT_SQL)) {
                    statement = activeConflictStatement;
                    when(statement.executeQuery()).thenReturn(conflict ? conflictingResult : emptyResult);
                } else if (sql.equals(JdbcClyvrDataRepository.ACTIVE_HOLD_CONFLICT_SQL)) {
                    statement = activeHoldConflictStatement;
                    when(statement.executeQuery()).thenReturn(activeHoldRows);
                } else if (sql.equals(JdbcClyvrDataRepository.EXPIRE_SLOT_HOLDS_SQL)) {
                    statement = expireHoldsStatement;
                } else if (sql.equals(JdbcClyvrDataRepository.EXPIRE_SLOT_HOLDS_EXCEPT_HOLD_SQL)) {
                    statement = expireHoldsStatement;
                } else if (sql.equals(JdbcClyvrDataRepository.TIME_OFF_CONFLICT_SQL)) {
                    statement = timeOffStatement;
                    when(statement.executeQuery()).thenReturn(emptyResult);
                } else if (sql.equals(JdbcClyvrDataRepository.RESCHEDULE_CONFLICT_SQL)) {
                    statement = rescheduleStatement;
                    when(statement.executeQuery()).thenReturn(emptyResult);
                } else if (sql.equals(JdbcClyvrDataRepository.ACTIVE_DOCTOR_SQL)) {
                    statement = activeDoctorStatement;
                    when(statement.executeQuery()).thenReturn(activeDoctorResult);
                } else if (sql.equals(JdbcClyvrDataRepository.INSERT_SQL)) {
                    statement = insertStatement;
                    when(statement.executeQuery()).thenReturn(insertedResult);
                } else if (sql.equals(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL)) {
                    statement = notificationStatement;
                    when(statement.executeUpdate()).thenReturn(1);
                } else if (sql.equals(JdbcClyvrDataRepository.INSERT_NOTIFICATIONS_FOR_ROLE_SQL)) {
                    statement = roleNotificationsStatement;
                    when(statement.executeUpdate()).thenReturn(1);
                } else {
                    throw new AssertionError("Unexpected SQL statement.");
                }
                statements.add(statement);
                return statement;
            });
        }
    }
}
