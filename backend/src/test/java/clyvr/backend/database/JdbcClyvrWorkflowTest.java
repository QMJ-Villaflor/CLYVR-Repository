package clyvr.backend.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import clyvr.backend.identity.BackendRole;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JdbcClyvrWorkflowTest {

    @Test
    void createsFiveMinuteHoldUnderSortedDoctorAndPatientLocks() throws Exception {
        Fixture fixture = new Fixture();
        fixture.query(JdbcClyvrDataRepository.LOCK_SQL,
                row("lock", 1), row("lock", 1));
        fixture.query(JdbcClyvrDataRepository.ACTIVE_DOCTOR_SQL, row("active", 1));
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-11-06T12:05:00Z");
        fixture.query(JdbcClyvrDataRepository.INSERT_HOLD_SQL,
                holdInsert(9901, expiresAt));
        LocalDate day = nextWeekday();

        AppointmentHold hold = new JdbcClyvrDataRepository(() -> fixture.connection)
                .createAppointmentHold(402, 701, day, 0);

        assertEquals(9901, hold.holdId());
        assertEquals(expiresAt, hold.expiresAt());
        assertTrue(JdbcClyvrDataRepository.INSERT_HOLD_SQL.contains(
                "CURRENT_TIMESTAMP + INTERVAL '5 minutes'"));
        ArgumentCaptor<String> lockKeys = ArgumentCaptor.forClass(String.class);
        verify(fixture.statement(JdbcClyvrDataRepository.LOCK_SQL), times(2))
                .setString(org.mockito.ArgumentMatchers.eq(1), lockKeys.capture());
        assertEquals(List.of("doctor:701:" + day + ":0", "patient:402:" + day + ":0"),
                lockKeys.getAllValues());
        verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
    }

    @Test
    void appointmentTimeOffRescheduleAndHoldConflictsBlockNewHold() throws Exception {
        for (String conflictingSql : List.of(
                JdbcClyvrDataRepository.ACTIVE_CONFLICT_SQL,
                JdbcClyvrDataRepository.TIME_OFF_CONFLICT_SQL,
                JdbcClyvrDataRepository.RESCHEDULE_CONFLICT_SQL,
                JdbcClyvrDataRepository.ACTIVE_HOLD_CONFLICT_SQL)) {
            Fixture fixture = new Fixture();
            fixture.query(JdbcClyvrDataRepository.LOCK_SQL,
                    row("lock", 1), row("lock", 1));
            fixture.query(JdbcClyvrDataRepository.ACTIVE_DOCTOR_SQL, row("active", 1));
            fixture.query(conflictingSql, row("occupied", 1));

            assertThrows(JdbcClyvrDataRepository.AppointmentConflictException.class,
                    () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                            .createAppointmentHold(402, 701, nextWeekday(), 0),
                    "Expected the slot conflict check to reject " + conflictingSql);

            verify(fixture.connection).rollback();
            verify(fixture.connection, never()).commit();
            verify(fixture.connection, never()).prepareStatement(
                    JdbcClyvrDataRepository.INSERT_HOLD_SQL);
        }
        assertTrue(JdbcClyvrDataRepository.ACTIVE_CONFLICT_SQL
                .contains("(doctor_id = ? OR patient_id = ?)"));
        assertTrue(JdbcClyvrDataRepository.ACTIVE_HOLD_CONFLICT_SQL
                .contains("expires_at > CURRENT_TIMESTAMP"));
        assertTrue(JdbcClyvrDataRepository.TIME_OFF_CONFLICT_SQL.contains("doctor_id = ?"));
        assertTrue(JdbcClyvrDataRepository.RESCHEDULE_CONFLICT_SQL.contains(
                "(a.doctor_id = ? OR a.patient_id = ?)"));
    }

    @Test
    void confirmsOwnedActiveHoldAndConvertsAtomicallyWithNotifications() throws Exception {
        Fixture fixture = new Fixture();
        LocalDate day = nextWeekday();
        fixture.query(JdbcClyvrDataRepository.FIND_PATIENT_HOLD_SQL,
                holdDetails(402, 701, day, 0));
        fixture.query(JdbcClyvrDataRepository.LOCK_SQL,
                row("lock", 1), row("lock", 1));
        fixture.query(JdbcClyvrDataRepository.LOCK_PATIENT_HOLD_SQL,
                lockedHold(402, 701, day, 0, "ACTIVE", true));
        fixture.query(JdbcClyvrDataRepository.ACTIVE_DOCTOR_SQL, row("active", 1));
        fixture.update(JdbcClyvrDataRepository.CONVERT_HOLD_SQL, 1);
        fixture.query(JdbcClyvrDataRepository.INSERT_SQL, row("appointment_id", 9003));
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL, 1);
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATIONS_FOR_ROLE_SQL, 2);

        long appointmentId = new JdbcClyvrDataRepository(() -> fixture.connection)
                .confirmAppointmentHold(402, 9901, "Synthetic reason");

        assertEquals(9003, appointmentId);
        verify(fixture.statement(JdbcClyvrDataRepository.CONVERT_HOLD_SQL)).setLong(1, 9901);
        verify(fixture.statement(JdbcClyvrDataRepository.CONVERT_HOLD_SQL)).setLong(2, 402);
        verify(fixture.statement(JdbcClyvrDataRepository.ACTIVE_HOLD_CONFLICT_SQL))
                .setLong(6, 9901);
        verify(fixture.statement(JdbcClyvrDataRepository.INSERT_SQL))
                .setString(5, "Synthetic reason");
        verify(fixture.statement(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL), times(1))
                .executeUpdate();
        verify(fixture.statement(JdbcClyvrDataRepository.INSERT_NOTIFICATIONS_FOR_ROLE_SQL))
                .setString(2, "RECEPTIONIST");
        verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
    }

    @Test
    void expiredHoldIsMarkedExpiredAndCommittedWithoutCreatingAppointment() throws Exception {
        Fixture fixture = new Fixture();
        LocalDate day = nextWeekday();
        fixture.query(JdbcClyvrDataRepository.FIND_PATIENT_HOLD_SQL,
                holdDetails(402, 701, day, 0));
        fixture.query(JdbcClyvrDataRepository.LOCK_SQL,
                row("lock", 1), row("lock", 1));
        fixture.query(JdbcClyvrDataRepository.LOCK_PATIENT_HOLD_SQL,
                lockedHold(402, 701, day, 0, "ACTIVE", false));
        fixture.update(JdbcClyvrDataRepository.EXPIRE_HOLD_SQL, 1);

        assertThrows(AppointmentHoldExpiredException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .confirmAppointmentHold(402, 9901, "Synthetic reason"));

        verify(fixture.statement(JdbcClyvrDataRepository.EXPIRE_HOLD_SQL))
                .setLong(1, 9901);
        verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
        verify(fixture.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.INSERT_SQL);
    }

    @Test
    void failedConfirmationRollsBackAndKeepsStillValidHold() throws Exception {
        Fixture fixture = new Fixture();
        LocalDate day = nextWeekday();
        fixture.query(JdbcClyvrDataRepository.FIND_PATIENT_HOLD_SQL,
                holdDetails(402, 701, day, 0));
        fixture.query(JdbcClyvrDataRepository.LOCK_SQL,
                row("lock", 1), row("lock", 1));
        fixture.query(JdbcClyvrDataRepository.LOCK_PATIENT_HOLD_SQL,
                lockedHold(402, 701, day, 0, "ACTIVE", true));
        fixture.query(JdbcClyvrDataRepository.ACTIVE_CONFLICT_SQL,
                row("occupied", 1));

        assertThrows(JdbcClyvrDataRepository.AppointmentConflictException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .confirmAppointmentHold(402, 9901, "Synthetic reason"));

        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).commit();
        verify(fixture.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.CONVERT_HOLD_SQL);
        verify(fixture.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.INSERT_SQL);
    }

    @Test
    void wrongPatientAndInactiveHoldCannotBeConfirmed() throws Exception {
        Fixture foreign = new Fixture();
        foreign.query(JdbcClyvrDataRepository.FIND_PATIENT_HOLD_SQL, empty());
        assertThrows(AppointmentNotFoundException.class,
                () -> new JdbcClyvrDataRepository(() -> foreign.connection)
                        .confirmAppointmentHold(999, 9901, "Synthetic reason"));
        verify(foreign.connection).rollback();
        verify(foreign.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.INSERT_SQL);

        Fixture stale = new Fixture();
        LocalDate day = nextWeekday();
        stale.query(JdbcClyvrDataRepository.FIND_PATIENT_HOLD_SQL,
                holdDetails(402, 701, day, 0));
        stale.query(JdbcClyvrDataRepository.LOCK_SQL,
                row("lock", 1), row("lock", 1));
        stale.query(JdbcClyvrDataRepository.LOCK_PATIENT_HOLD_SQL,
                lockedHold(402, 701, day, 0, "RELEASED", true));
        assertThrows(InvalidAppointmentTransitionException.class,
                () -> new JdbcClyvrDataRepository(() -> stale.connection)
                        .confirmAppointmentHold(402, 9901, "Synthetic reason"));
        verify(stale.connection).rollback();
        verify(stale.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.INSERT_SQL);
    }

    @Test
    void releaseOnlyUpdatesThePatientOwnedActiveHold() throws Exception {
        Fixture owner = new Fixture();
        owner.update(JdbcClyvrDataRepository.RELEASE_HOLD_SQL, 1);
        assertTrue(new JdbcClyvrDataRepository(() -> owner.connection)
                .releaseAppointmentHold(402, 9901));
        verify(owner.statement(JdbcClyvrDataRepository.RELEASE_HOLD_SQL)).setLong(1, 9901);
        verify(owner.statement(JdbcClyvrDataRepository.RELEASE_HOLD_SQL)).setLong(2, 402);

        Fixture foreign = new Fixture();
        foreign.update(JdbcClyvrDataRepository.RELEASE_HOLD_SQL, 0);
        assertFalse(new JdbcClyvrDataRepository(() -> foreign.connection)
                .releaseAppointmentHold(999, 9901));
    }

    @Test
    void submitsTimeOffWithIdentityGeneratedKeyAndReceptionistNotification() throws Exception {
        Fixture fixture = new Fixture();
        fixture.query(JdbcClyvrDataRepository.LOCK_DOCTOR_SLOT_SQL, row("unused", 1));
        fixture.query(JdbcClyvrDataRepository.TIME_OFF_SLOT_CONFLICT_SQL, empty());
        ResultSet inserted = row("time_off_id", 7101);
        fixture.query(JdbcClyvrDataRepository.INSERT_TIME_OFF_SQL, inserted);
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATIONS_FOR_ROLE_SQL, 2);
        LocalDate day = nextWeekday();

        long id = new JdbcClyvrDataRepository(() -> fixture.connection)
                .submitTimeOff(701, day, 0, "Synthetic leave");

        assertEquals(7101, id);
        verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
        PreparedStatement insert = fixture.statement(JdbcClyvrDataRepository.INSERT_TIME_OFF_SQL);
        verify(insert).setLong(1, 701);
        verify(insert).setObject(2, day);
        verify(insert).setInt(3, 0);
        verify(insert).setString(4, "Synthetic leave");
        verify(fixture.statement(JdbcClyvrDataRepository.INSERT_NOTIFICATIONS_FOR_ROLE_SQL))
                .setString(2, "RECEPTIONIST");
    }

    @Test
    void invalidTimeOffDoesNotOpenDatabaseConnection() {
        JdbcClyvrDataRepository repository = new JdbcClyvrDataRepository(
                () -> { throw new AssertionError("Invalid input must not connect."); });
        assertThrows(IllegalArgumentException.class,
                () -> repository.submitTimeOff(701, LocalDate.now(), 8, "Invalid"));
    }

    @Test
    void createsEligibleDoctorProposalTransactionallyAndNotifiesPatient() throws Exception {
        Fixture fixture = new Fixture();
        fixture.query(JdbcClyvrDataRepository.ELIGIBLE_PROPOSAL_PATIENT_SQL,
                row("eligible", 1));
        fixture.query(JdbcClyvrDataRepository.LOCK_SQL,
                row("lock", 1), row("lock", 1));
        fixture.query(JdbcClyvrDataRepository.ACTIVE_CONFLICT_SQL, empty());
        fixture.query(JdbcClyvrDataRepository.TIME_OFF_CONFLICT_SQL, empty());
        fixture.query(JdbcClyvrDataRepository.RESCHEDULE_CONFLICT_SQL, empty());
        fixture.query(JdbcClyvrDataRepository.INSERT_DOCTOR_PROPOSAL_SQL,
                row("appointment_id", 9101));
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL, 1);
        LocalDate day = nextWeekday();

        long id = new JdbcClyvrDataRepository(() -> fixture.connection)
                .createDoctorProposal(701, 402, "FOLLOW_UP", day, 0, "Review symptoms");

        assertEquals(9101, id);
        PreparedStatement insert = fixture.statement(
                JdbcClyvrDataRepository.INSERT_DOCTOR_PROPOSAL_SQL);
        verify(insert).setLong(1, 402);
        verify(insert).setLong(2, 701);
        verify(insert).setString(3, "FOLLOW_UP");
        verify(insert).setObject(4, day);
        verify(insert).setInt(5, 0);
        verify(insert).setString(6, "Review symptoms");
        verify(fixture.connection).commit();
        verify(fixture.statement(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL))
                .setLong(1, 402);
    }

    @Test
    void doctorProposalRejectsAnExistingUnexpiredActiveHold() throws Exception {
        Fixture fixture = new Fixture();
        fixture.query(JdbcClyvrDataRepository.ELIGIBLE_PROPOSAL_PATIENT_SQL,
                row("eligible", 1));
        fixture.query(JdbcClyvrDataRepository.LOCK_SQL,
                row("lock", 1), row("lock", 1));
        fixture.query(JdbcClyvrDataRepository.ACTIVE_HOLD_CONFLICT_SQL,
                row("hold_id", 9901));

        assertThrows(JdbcClyvrDataRepository.AppointmentConflictException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .createDoctorProposal(701, 402, "FOLLOW_UP",
                                nextWeekday(), 0, "Synthetic proposal"));

        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.INSERT_DOCTOR_PROPOSAL_SQL);
        verify(fixture.statement(JdbcClyvrDataRepository.ACTIVE_HOLD_CONFLICT_SQL))
                .setLong(4, 402);
    }

    @Test
    void rejectsInvalidDoctorProposalBeforeOpeningDatabaseConnection() {
        JdbcClyvrDataRepository repository = new JdbcClyvrDataRepository(
                () -> { throw new AssertionError("Invalid proposal must not connect."); });
        assertThrows(IllegalArgumentException.class,
                () -> repository.createDoctorProposal(
                        701, 402, "PROCEDURE", nextWeekday(), 0, "Wrong slot type"));
    }

    @Test
    void doctorCannotProposeToPatientWithoutCompletedConsultation() throws Exception {
        Fixture fixture = new Fixture();
        fixture.query(JdbcClyvrDataRepository.ELIGIBLE_PROPOSAL_PATIENT_SQL, empty());

        assertThrows(AppointmentNotFoundException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .createDoctorProposal(
                                701, 999, "FOLLOW_UP", nextWeekday(), 0, "Synthetic proposal"));

        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.INSERT_DOCTOR_PROPOSAL_SQL);
        verify(fixture.connection, never()).prepareStatement(JdbcClyvrDataRepository.LOCK_SQL);
    }

    @Test
    void rollsBackDoctorProposalWhenTheChosenSlotHasConflicted() throws Exception {
        Fixture fixture = new Fixture();
        fixture.query(JdbcClyvrDataRepository.ELIGIBLE_PROPOSAL_PATIENT_SQL,
                row("eligible", 1));
        fixture.query(JdbcClyvrDataRepository.LOCK_SQL,
                row("lock", 1), row("lock", 1));
        fixture.query(JdbcClyvrDataRepository.ACTIVE_CONFLICT_SQL, row("occupied", 1));

        assertThrows(JdbcClyvrDataRepository.AppointmentConflictException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .createDoctorProposal(
                                701, 402, "FOLLOW_UP", nextWeekday(), 0, "Review symptoms"));

        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.INSERT_DOCTOR_PROPOSAL_SQL);
    }

    @Test
    void rejectsTimeOffAndPersistsReviewerAndNotification() throws Exception {
        Fixture fixture = new Fixture();
        ResultSet request = row("doctor_id", 701, "slot_number", 0,
                "status", "pending", null, null, null, null);
        when(request.getObject("request_date", LocalDate.class)).thenReturn(nextWeekday());
        fixture.query(JdbcClyvrDataRepository.LOCK_TIME_OFF_SQL, request);
        fixture.query(JdbcClyvrDataRepository.LOCK_DOCTOR_SLOT_SQL, row("unused", 1));
        fixture.update(JdbcClyvrDataRepository.SET_TIME_OFF_DECISION_SQL, 1);
        fixture.query(JdbcClyvrDataRepository.DOCTOR_USER_SQL, row("user_id", 503));
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL, 1);

        new JdbcClyvrDataRepository(() -> fixture.connection)
                .decideTimeOff(407, 7101, false, "Coverage is unavailable.");

        PreparedStatement update = fixture.statement(
                JdbcClyvrDataRepository.SET_TIME_OFF_DECISION_SQL);
        verify(update).setString(1, "rejected");
        verify(update).setLong(2, 407);
        verify(update).setLong(3, 7101);
        verify(fixture.connection).commit();
        verify(fixture.statement(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL))
                .setLong(1, 503);
    }

    @Test
    void doesNotApproveTimeOffWhenAppointmentOrRescheduleOccupiesTheSlot() throws Exception {
        Fixture fixture = new Fixture();
        ResultSet request = row("doctor_id", 701, "slot_number", 0,
                "status", "pending", null, null, null, null);
        when(request.getObject("request_date", LocalDate.class)).thenReturn(nextWeekday());
        fixture.query(JdbcClyvrDataRepository.LOCK_TIME_OFF_SQL, request);
        fixture.query(JdbcClyvrDataRepository.LOCK_DOCTOR_SLOT_SQL, row("unused", 1));
        fixture.query(JdbcClyvrDataRepository.ACTIVE_APPOINTMENT_OR_RESCHEDULE_SQL,
                row("occupied", 1));

        assertThrows(JdbcClyvrDataRepository.AppointmentConflictException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .decideTimeOff(407, 7101, true, "Approved"));

        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.SET_TIME_OFF_DECISION_SQL);
    }

    @Test
    void submitsPatientCancellationRequestWithoutChangingAppointmentStatus() throws Exception {
        Fixture fixture = new Fixture();
        fixture.query(JdbcClyvrDataRepository.LOCK_PATIENT_APPOINTMENT_FOR_CHANGE_SQL,
                appointment("CONFIRMED"));
        fixture.query(JdbcClyvrDataRepository.PENDING_DUPLICATE_CHANGE_SQL, empty());
        fixture.query(JdbcClyvrDataRepository.INSERT_CHANGE_SQL, row("request_id", 8101));
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATIONS_FOR_ROLE_SQL, 1);
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL, 1);

        long requestId = new JdbcClyvrDataRepository(() -> fixture.connection)
                .submitAppointmentChange(BackendRole.PATIENT, 402, null, 9001,
                        "cancellation", "Synthetic cancellation", null, null);

        assertEquals(8101, requestId);
        verify(fixture.connection).commit();
        verify(fixture.connection, never()).prepareStatement(
                org.mockito.ArgumentMatchers.contains("UPDATE public.appointments"));
        PreparedStatement insert = fixture.statement(JdbcClyvrDataRepository.INSERT_CHANGE_SQL);
        verify(insert).setLong(1, 9001);
        verify(insert).setLong(2, 402);
        verify(insert).setString(3, "cancellation");
    }

    @Test
    void approvesRescheduleAtomicallyAndPersistsLowercaseRequestStatus() throws Exception {
        Fixture fixture = new Fixture();
        LocalDate replacement = nextWeekday();
        ResultSet request = row("request_id", 8102, "appointment_id", 9001,
                "requester_id", 402, "kind", "reschedule", "status", "pending");
        when(request.getObject("proposed_date", LocalDate.class)).thenReturn(replacement);
        when(request.getObject("proposed_slot")).thenReturn(2);
        fixture.query(JdbcClyvrDataRepository.LOCK_CHANGE_SQL, request);
        ResultSet appointment = appointment("CONFIRMED");
        fixture.query(JdbcClyvrDataRepository.LOCK_LINKED_APPOINTMENT_SQL, appointment);
        fixture.query(JdbcClyvrDataRepository.LOCK_SQL,
                row("unused", 1), row("unused", 1));
        fixture.query(JdbcClyvrDataRepository.CHANGE_SLOT_CONFLICT_SQL, empty());
        fixture.update(JdbcClyvrDataRepository.UPDATE_APPOINTMENT_SLOT_SQL, 1);
        fixture.update(JdbcClyvrDataRepository.UPDATE_CHANGE_DECISION_SQL, 1);
        fixture.query(JdbcClyvrDataRepository.DOCTOR_USER_SQL, row("user_id", 503));
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL, 1);

        new JdbcClyvrDataRepository(() -> fixture.connection)
                .decideAppointmentChange(407, 8102, true, "Approved");

        verify(fixture.connection).commit();
        verify(fixture.statement(JdbcClyvrDataRepository.UPDATE_APPOINTMENT_SLOT_SQL))
                .setObject(1, replacement);
        verify(fixture.statement(JdbcClyvrDataRepository.UPDATE_CHANGE_DECISION_SQL))
                .setString(1, "approved");
        verify(fixture.statement(JdbcClyvrDataRepository.UPDATE_CHANGE_DECISION_SQL))
                .setLong(3, 407);
    }

    @Test
    void rejectsApprovalOfARescheduleThatIsNoLongerInTheFuture() throws Exception {
        Fixture fixture = new Fixture();
        ResultSet request = row("request_id", 8102, "appointment_id", 9001,
                "requester_id", 402, "kind", "reschedule", "status", "pending");
        when(request.getObject("proposed_date", LocalDate.class))
                .thenReturn(LocalDate.now().minusDays(1));
        when(request.getObject("proposed_slot")).thenReturn(2);
        fixture.query(JdbcClyvrDataRepository.LOCK_CHANGE_SQL, request);
        fixture.query(JdbcClyvrDataRepository.LOCK_LINKED_APPOINTMENT_SQL,
                appointment("CONFIRMED"));

        assertThrows(InvalidAppointmentTransitionException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .decideAppointmentChange(407, 8102, true, "Approved"));

        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.UPDATE_APPOINTMENT_SLOT_SQL);
        verify(fixture.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.UPDATE_CHANGE_DECISION_SQL);
    }

    @Test
    void approvesProposalForOwningPatientWithAtomicSlotUpdateAndNotifications() throws Exception {
        Fixture fixture = new Fixture();
        LocalDate alternativeDate = nextWeekday();
        fixture.query(JdbcClyvrDataRepository.LOCK_PATIENT_PROPOSAL_SQL,
                proposal(9101, 402, 701, "FOLLOW_UP", nextWeekday(), 0, "PENDING_PATIENT"));
        fixture.query(JdbcClyvrDataRepository.LOCK_SQL,
                row("lock", 1), row("lock", 1));
        fixture.query(JdbcClyvrDataRepository.CHANGE_SLOT_CONFLICT_SQL, empty());
        fixture.update(JdbcClyvrDataRepository.CONFIRM_PROPOSAL_SQL, 1);
        fixture.query(JdbcClyvrDataRepository.APPOINTMENT_RECIPIENTS_SQL,
                row("patient_id", 402, "doctor_user_id", 503));
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL, 1);

        new JdbcClyvrDataRepository(() -> fixture.connection)
                .decideDoctorProposal(402, 9101, true, alternativeDate, 2);

        PreparedStatement update = fixture.statement(JdbcClyvrDataRepository.CONFIRM_PROPOSAL_SQL);
        verify(update).setObject(1, alternativeDate);
        verify(update).setInt(2, 2);
        verify(update).setLong(3, 9101);
        verify(update).setLong(4, 402);
        verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
        verify(fixture.statement(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL))
                .setLong(1, 503);
    }

    @Test
    void staleOrForeignProposalCannotBeApproved() throws Exception {
        Fixture fixture = new Fixture();
        fixture.query(JdbcClyvrDataRepository.LOCK_PATIENT_PROPOSAL_SQL,
                proposal(9101, 402, 701, "FOLLOW_UP", nextWeekday(), 0, "CONFIRMED"));

        assertThrows(InvalidAppointmentTransitionException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .decideDoctorProposal(402, 9101, true, null, null));

        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.CONFIRM_PROPOSAL_SQL);

        Fixture foreignFixture = new Fixture();
        foreignFixture.query(JdbcClyvrDataRepository.LOCK_PATIENT_PROPOSAL_SQL, empty());
        assertThrows(AppointmentNotFoundException.class,
                () -> new JdbcClyvrDataRepository(() -> foreignFixture.connection)
                        .decideDoctorProposal(999, 9101, true, null, null));
        verify(foreignFixture.connection).rollback();
    }

    @Test
    void declinesProposalForOwningPatientTransactionallyAndNotifiesBothParties() throws Exception {
        Fixture fixture = new Fixture();
        fixture.query(JdbcClyvrDataRepository.LOCK_PATIENT_PROPOSAL_SQL,
                proposal(9101, 402, 701, "FOLLOW_UP", nextWeekday(), 0, "PENDING_PATIENT"));
        fixture.update(JdbcClyvrDataRepository.DECLINE_PROPOSAL_SQL, 1);
        fixture.query(JdbcClyvrDataRepository.APPOINTMENT_RECIPIENTS_SQL,
                row("patient_id", 402, "doctor_user_id", 503));
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL, 1);

        new JdbcClyvrDataRepository(() -> fixture.connection)
                .decideDoctorProposal(402, 9101, false, null, null);

        PreparedStatement decline = fixture.statement(JdbcClyvrDataRepository.DECLINE_PROPOSAL_SQL);
        verify(decline).setLong(1, 9101);
        verify(decline).setLong(2, 402);
        PreparedStatement notification =
                fixture.statement(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL);
        ArgumentCaptor<Long> recipients = ArgumentCaptor.forClass(Long.class);
        verify(notification, times(2)).setLong(org.mockito.ArgumentMatchers.eq(1),
                recipients.capture());
        assertEquals(List.of(402L, 503L), recipients.getAllValues());
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(notification, times(2)).setString(org.mockito.ArgumentMatchers.eq(2),
                messages.capture());
        assertTrue(messages.getAllValues().stream().allMatch(message ->
                message.contains("DECLINED")));
        verify(notification, times(2)).executeUpdate();
        verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
    }

    @Test
    void wrongPatientAndRepeatedProposalDeclineAreRejected() throws Exception {
        Fixture foreignFixture = new Fixture();
        foreignFixture.query(JdbcClyvrDataRepository.LOCK_PATIENT_PROPOSAL_SQL, empty());
        assertThrows(AppointmentNotFoundException.class,
                () -> new JdbcClyvrDataRepository(() -> foreignFixture.connection)
                        .decideDoctorProposal(999, 9101, false, null, null));
        verify(foreignFixture.connection).rollback();
        verify(foreignFixture.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.DECLINE_PROPOSAL_SQL);

        Fixture staleFixture = new Fixture();
        staleFixture.query(JdbcClyvrDataRepository.LOCK_PATIENT_PROPOSAL_SQL,
                proposal(9101, 402, 701, "FOLLOW_UP", nextWeekday(), 0, "DECLINED"));
        assertThrows(InvalidAppointmentTransitionException.class,
                () -> new JdbcClyvrDataRepository(() -> staleFixture.connection)
                        .decideDoctorProposal(402, 9101, false, null, null));
        verify(staleFixture.connection).rollback();
        verify(staleFixture.connection, never()).prepareStatement(
                JdbcClyvrDataRepository.DECLINE_PROPOSAL_SQL);
    }

    @Test
    void rollsBackProposalDeclineWhenNotificationCannotBeSaved() throws Exception {
        Fixture fixture = new Fixture();
        fixture.query(JdbcClyvrDataRepository.LOCK_PATIENT_PROPOSAL_SQL,
                proposal(9101, 402, 701, "FOLLOW_UP", nextWeekday(), 0, "PENDING_PATIENT"));
        fixture.update(JdbcClyvrDataRepository.DECLINE_PROPOSAL_SQL, 1);
        fixture.query(JdbcClyvrDataRepository.APPOINTMENT_RECIPIENTS_SQL,
                row("patient_id", 402, "doctor_user_id", 503));
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL, 1);
        when(fixture.statement(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL)
                .executeUpdate()).thenThrow(new SQLException("synthetic notification failure"));

        assertThrows(SQLException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .decideDoctorProposal(402, 9101, false, null, null));

        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).commit();
    }

    @Test
    void declinedAndRejectedStatusesDoNotReserveSlots() {
        for (String sql : new String[] {
                JdbcClyvrDataRepository.APPOINTMENTS_SQL,
                JdbcClyvrDataRepository.DOCTOR_SCHEDULE_APPOINTMENTS_SQL,
                JdbcClyvrDataRepository.ACTIVE_CONFLICT_SQL,
                JdbcClyvrDataRepository.TIME_OFF_SLOT_CONFLICT_SQL,
                JdbcClyvrDataRepository.CHANGE_SLOT_CONFLICT_SQL
        }) {
            assertFalse(sql.contains("'REJECTED'"));
            assertFalse(sql.contains("'DECLINED'"));
            assertTrue(sql.contains("'PENDING_RECEPTIONIST'"));
            assertTrue(sql.contains("'PENDING_PATIENT'"));
            assertTrue(sql.contains("'CONFIRMED'"));
        }
    }

    @Test
    void rejectsCancellationRequestWithoutChangingItsAppointment() throws Exception {
        Fixture fixture = new Fixture();
        fixture.query(JdbcClyvrDataRepository.LOCK_CHANGE_SQL,
                row("request_id", 8101, "appointment_id", 9001,
                        "requester_id", 402, "kind", "cancellation", "status", "pending"));
        fixture.query(JdbcClyvrDataRepository.LOCK_LINKED_APPOINTMENT_SQL,
                appointment("CONFIRMED"));
        fixture.update(JdbcClyvrDataRepository.UPDATE_CHANGE_DECISION_SQL, 1);
        fixture.query(JdbcClyvrDataRepository.DOCTOR_USER_SQL, row("user_id", 503));
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL, 1);

        new JdbcClyvrDataRepository(() -> fixture.connection)
                .decideAppointmentChange(407, 8101, false, "Patient changed plans.");

        verify(fixture.connection).commit();
        verify(fixture.connection, never()).prepareStatement(
                org.mockito.ArgumentMatchers.contains("UPDATE public.appointments"));
        PreparedStatement update = fixture.statement(
                JdbcClyvrDataRepository.UPDATE_CHANGE_DECISION_SQL);
        verify(update).setString(1, "rejected");
        verify(update).setString(2, "Patient changed plans.");
        verify(update).setLong(3, 407);
    }

    @Test
    void retrievesNotificationsAndScopesReadUpdateToRecipient() throws Exception {
        Fixture fixture = new Fixture();
        ResultSet notification = mock(ResultSet.class);
        OffsetDateTime created = OffsetDateTime.parse("2026-11-06T09:30:00Z");
        when(notification.next()).thenReturn(true, false);
        when(notification.getLong("notification_id")).thenReturn(8201L);
        when(notification.getString("message")).thenReturn("Synthetic notice");
        when(notification.getObject("created_at", OffsetDateTime.class)).thenReturn(created);
        when(notification.getBoolean("is_read")).thenReturn(false);
        fixture.query(JdbcClyvrDataRepository.NOTIFICATIONS_SQL, notification);
        fixture.update(JdbcClyvrDataRepository.MARK_NOTIFICATION_READ_SQL, 1);
        JdbcClyvrDataRepository repository = new JdbcClyvrDataRepository(
                () -> fixture.connection);

        var notices = repository.findNotifications(402);
        assertEquals(1, notices.size());
        assertEquals(new BackendNotification(8201, "Synthetic notice", created, false),
                notices.get(0));
        verify(fixture.statement(JdbcClyvrDataRepository.NOTIFICATIONS_SQL)).setLong(1, 402);
        repository.markNotificationRead(402, 8201);
        verify(fixture.statement(JdbcClyvrDataRepository.MARK_NOTIFICATION_READ_SQL))
                .setLong(2, 402);
    }

    @Test
    void createsDoctorNotificationOnlyForAnAssignedConfirmedAppointment() throws Exception {
        Fixture fixture = new Fixture();
        fixture.query(JdbcClyvrDataRepository.DOCTOR_NOTIFY_APPOINTMENT_SQL,
                row("patient_id", 402));
        fixture.update(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL, 1);

        new JdbcClyvrDataRepository(() -> fixture.connection)
                .notifyUser(701, 9001, "Please contact the clinic.");

        verify(fixture.statement(JdbcClyvrDataRepository.DOCTOR_NOTIFY_APPOINTMENT_SQL))
                .setLong(2, 701);
        verify(fixture.statement(JdbcClyvrDataRepository.INSERT_NOTIFICATION_SQL))
                .setLong(1, 402);
        verify(fixture.connection).commit();
    }

    @Test
    void failedNotificationReadCannotMarkAnotherUsersRecordAndDatabaseFailurePropagates()
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.update(JdbcClyvrDataRepository.MARK_NOTIFICATION_READ_SQL, 0);
        assertThrows(AppointmentNotFoundException.class,
                () -> new JdbcClyvrDataRepository(() -> fixture.connection)
                        .markNotificationRead(402, 8201));
        PreparedStatement update = fixture.statement(
                JdbcClyvrDataRepository.MARK_NOTIFICATION_READ_SQL);
        verify(update).setLong(1, 8201);
        verify(update).setLong(2, 402);

        Connection failedConnection = mock(Connection.class);
        PreparedStatement failed = mock(PreparedStatement.class);
        when(failedConnection.prepareStatement(JdbcClyvrDataRepository.MARK_NOTIFICATION_READ_SQL))
                .thenReturn(failed);
        when(failed.executeUpdate()).thenThrow(new SQLException("synthetic write failure"));
        assertThrows(SQLException.class,
                () -> new JdbcClyvrDataRepository(() -> failedConnection)
                        .markNotificationRead(402, 8201));
    }

    private static ResultSet appointment(String status) throws Exception {
        ResultSet result = row("appointment_id", 9001, "patient_id", 402, "doctor_id", 701,
                "slot_number", 0, null, null);
        when(result.getString("appointment_type")).thenReturn("CONSULTATION");
        when(result.getString("status")).thenReturn(status);
        when(result.getObject("appointment_date", LocalDate.class)).thenReturn(nextWeekday());
        return result;
    }

    private static ResultSet proposal(
            long appointmentId, long patientId, long doctorId, String appointmentType,
            LocalDate day, int slot, String status) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        when(result.getLong("appointment_id")).thenReturn(appointmentId);
        when(result.getLong("patient_id")).thenReturn(patientId);
        when(result.getLong("doctor_id")).thenReturn(doctorId);
        when(result.getString("appointment_type")).thenReturn(appointmentType);
        when(result.getObject("appointment_date", LocalDate.class)).thenReturn(day);
        when(result.getInt("slot_number")).thenReturn(slot);
        when(result.getString("status")).thenReturn(status);
        when(result.wasNull()).thenReturn(false);
        return result;
    }

    private static ResultSet holdInsert(long holdId, OffsetDateTime expiresAt) throws Exception {
        ResultSet result = row("hold_id", holdId);
        when(result.getObject("expires_at", OffsetDateTime.class)).thenReturn(expiresAt);
        return result;
    }

    private static ResultSet holdDetails(
            long patientId, long doctorId, LocalDate day, int slot) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        when(result.getLong("patient_id")).thenReturn(patientId);
        when(result.getLong("doctor_id")).thenReturn(doctorId);
        when(result.getInt("slot_number")).thenReturn(slot);
        when(result.getObject("appointment_date", LocalDate.class)).thenReturn(day);
        when(result.wasNull()).thenReturn(false);
        return result;
    }

    private static ResultSet lockedHold(
            long patientId, long doctorId, LocalDate day, int slot,
            String status, boolean unexpired) throws Exception {
        ResultSet result = row("hold_id", 9901, "patient_id", patientId,
                "doctor_id", doctorId, "slot_number", slot, null, null);
        when(result.getObject("appointment_date", LocalDate.class)).thenReturn(day);
        when(result.getString("status")).thenReturn(status);
        when(result.getBoolean("is_unexpired")).thenReturn(unexpired);
        return result;
    }

    private static ResultSet row(String firstColumn, Object firstValue) throws Exception {
        return row(firstColumn, firstValue, null, null, null, null, null, null, null, null);
    }

    private static ResultSet row(
            String firstColumn, Object firstValue,
            String secondColumn, Object secondValue) throws Exception {
        return row(firstColumn, firstValue, secondColumn, secondValue,
                null, null, null, null, null, null);
    }

    private static ResultSet row(
            String firstColumn, Object firstValue,
            String secondColumn, Object secondValue,
            String thirdColumn, Object thirdValue,
            String fourthColumn, Object fourthValue,
            String fifthColumn, Object fifthValue) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        for (String column : new String[] {
                firstColumn, secondColumn, thirdColumn, fourthColumn, fifthColumn}) {
            if (column != null) {
                when(result.getLong(column)).thenReturn(0L);
            }
        }
        setLong(result, firstColumn, firstValue);
        setLong(result, secondColumn, secondValue);
        setLong(result, thirdColumn, thirdValue);
        setLong(result, fourthColumn, fourthValue);
        setLong(result, fifthColumn, fifthValue);
        when(result.wasNull()).thenReturn(false);
        return result;
    }

    private static void setLong(ResultSet result, String column, Object value) throws Exception {
        if (column != null && value instanceof Number number) {
            when(result.getLong(column)).thenReturn(number.longValue());
        }
        if (column != null && value instanceof String text) {
            when(result.getString(column)).thenReturn(text);
        }
    }

    private static ResultSet empty() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(false);
        return result;
    }

    private static LocalDate nextWeekday() {
        LocalDate day = LocalDate.now().plusDays(1);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY
                || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    private static final class Fixture {
        private final Connection connection = mock(Connection.class);
        private final Map<String, PreparedStatement> statements = new HashMap<>();

        private Fixture() throws Exception {
            when(connection.getAutoCommit()).thenReturn(true);
            query(JdbcClyvrDataRepository.ACTIVE_CONFLICT_SQL, empty());
            query(JdbcClyvrDataRepository.TIME_OFF_CONFLICT_SQL, empty());
            query(JdbcClyvrDataRepository.RESCHEDULE_CONFLICT_SQL, empty());
            query(JdbcClyvrDataRepository.ACTIVE_HOLD_CONFLICT_SQL, empty());
            query(JdbcClyvrDataRepository.ACTIVE_DOCTOR_SQL, row("active", 1));
            update(JdbcClyvrDataRepository.EXPIRE_SLOT_HOLDS_SQL, 0);
            update(JdbcClyvrDataRepository.EXPIRE_SLOT_HOLDS_EXCEPT_HOLD_SQL, 0);
            update(JdbcClyvrDataRepository.EXPIRE_HOLD_SQL, 0);
            when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                PreparedStatement statement = statements.get(sql);
                if (statement == null) {
                    throw new AssertionError("Unexpected SQL: " + sql);
                }
                return statement;
            });
        }

        private void query(String sql, ResultSet... results) throws Exception {
            PreparedStatement statement = mock(PreparedStatement.class);
            int[] execution = {0};
            when(statement.executeQuery()).thenAnswer(invocation ->
                    results[Math.min(execution[0]++, results.length - 1)]);
            statements.put(sql, statement);
        }

        private void update(String sql, int rowCount) throws Exception {
            PreparedStatement statement = mock(PreparedStatement.class);
            when(statement.executeUpdate()).thenReturn(rowCount);
            statements.put(sql, statement);
        }

        private PreparedStatement statement(String sql) {
            return statements.get(sql);
        }
    }
}
