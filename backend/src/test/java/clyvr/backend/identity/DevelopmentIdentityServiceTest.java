package clyvr.backend.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import clyvr.backend.database.ClyvrDataRepository;
import clyvr.backend.database.ResolvedDatabaseIdentity;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DevelopmentIdentityServiceTest {

    @Test
    void resolvesDemoKeyToMappedDatabaseUserAndUsesDatabaseRole() throws Exception {
        DemoIdentityMappings mappings = new DemoIdentityMappings(Map.of(
                "CLYVR_DEMO_USER_ID_U2", "402",
                "CLYVR_DEMO_DOCTOR_ID_D1", "701"));
        ClyvrDataRepository repository = new IdentityRepository(
                new ResolvedDatabaseIdentity(402, BackendRole.PATIENT, null));
        DevelopmentIdentityService service =
                new DevelopmentIdentityService(true, mappings, repository);

        DevelopmentIdentity identity = service.resolve("U2", "127.0.0.1");

        assertEquals(402, identity.userId());
        assertEquals(BackendRole.PATIENT, identity.role());
        assertEquals("U2", identity.demoKey());
    }

    @Test
    void rejectsNonLoopbackAndDisabledDevelopmentIdentity() {
        DemoIdMappingFixture fixture = new DemoIdMappingFixture();
        DevelopmentIdentityService enabled =
                new DevelopmentIdentityService(true, fixture.mappings, fixture.repository);
        DevelopmentIdentityService disabled =
                new DevelopmentIdentityService(false, fixture.mappings, fixture.repository);

        assertThrows(DevelopmentIdentityService.DevelopmentIdentityUnavailableException.class,
                () -> enabled.resolve("U2", "192.168.1.25"));
        assertThrows(DevelopmentIdentityService.DevelopmentIdentityUnavailableException.class,
                () -> disabled.resolve("U2", "127.0.0.1"));
    }

    @Test
    void rejectsUnknownDemoKeyAndMissingMapping() {
        DevelopmentIdentityService service = new DevelopmentIdentityService(
                true,
                new DemoIdentityMappings(Map.of()),
                new IdentityRepository(null));

        assertThrows(IllegalArgumentException.class,
                () -> service.resolve("D1", "127.0.0.1"));
        assertThrows(IllegalStateException.class,
                () -> service.resolve("U2", "127.0.0.1"));
    }

    private static final class DemoIdMappingFixture {
        private final DemoIdentityMappings mappings = new DemoIdentityMappings(
                Map.of("CLYVR_DEMO_USER_ID_U2", "402"));
        private final ClyvrDataRepository repository = new IdentityRepository(
                new ResolvedDatabaseIdentity(402, BackendRole.PATIENT, null));
    }

    private static final class IdentityRepository implements ClyvrDataRepository {
        private final ResolvedDatabaseIdentity identity;

        private IdentityRepository(ResolvedDatabaseIdentity identity) {
            this.identity = identity;
        }

        @Override
        public Optional<clyvr.backend.security.UserCredential> findCredential(String login) {
            return Optional.empty();
        }

        @Override
        public Optional<clyvr.backend.security.UserCredential> findCredential(long userId) {
            return Optional.empty();
        }

        @Override
        public long registerPatient(
                String email, String passwordHash, String firstName, String lastName,
                String phone, java.time.LocalDate birthDate, String sex, String address) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updatePasswordHash(long userId, String passwordHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ResolvedDatabaseIdentity> findActiveIdentity(long userId) {
            return identity != null && identity.userId() == userId
                    ? Optional.of(identity) : Optional.empty();
        }

        @Override
        public java.util.List<clyvr.backend.database.BackendDoctorEntry> findMappedDoctors(
                Map<String, Long> mappedDoctorIds) {
            return java.util.List.of();
        }

        @Override
        public clyvr.backend.database.BackendAvailability findAvailability(
                long patientId, long doctorId, java.time.LocalDate day) {
            throw new UnsupportedOperationException();
        }

        @Override
        public clyvr.backend.database.BackendAvailability findDoctorAvailability(
                long doctorId, java.time.LocalDate day) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<clyvr.backend.database.BackendConsultationRequest>
                findPendingConsultations() {
            return java.util.List.of();
        }

        @Override
        public long insertConsultation(
                long patientId, long doctorId, java.time.LocalDate day, int slot, String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public clyvr.backend.database.AppointmentHold createAppointmentHold(
                long patientId, long doctorId, java.time.LocalDate day, int slot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long confirmAppointmentHold(long patientId, long holdId, String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean releaseAppointmentHold(long patientId, long holdId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long createDoctorProposal(
                long doctorId, long patientId, String appointmentType,
                java.time.LocalDate day, int slot, String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void decideDoctorProposal(
                long patientId, long appointmentId, boolean approved,
                java.time.LocalDate alternativeDate, Integer alternativeSlot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<clyvr.backend.database.BackendPatientProfile> findPatientProfile(
                long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public clyvr.backend.database.BackendPatientProfile updatePatientProfile(
                long userId, clyvr.backend.database.PatientProfileUpdate update) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<clyvr.backend.database.BackendAppointment> findAppointments(
                BackendRole role, long userId, Long doctorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<clyvr.backend.database.BackendAppointment> findAppointment(
                BackendRole role, long userId, Long doctorId, long appointmentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void decidePendingConsultation(long appointmentId, boolean approved) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancelPatientAppointment(long userId, long appointmentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void finishDoctorAppointment(long doctorId, long appointmentId, String newStatus) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<clyvr.backend.database.BackendTimeOffRequest> findDoctorTimeOff(
                long doctorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<clyvr.backend.database.BackendTimeOffRequest> findPendingTimeOff() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long submitTimeOff(long doctorId, java.time.LocalDate day, int slot, String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void decideTimeOff(
                long receptionistId, long timeOffId, boolean approved, String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<clyvr.backend.database.BackendAppointmentChangeRequest>
                findPendingAppointmentChanges() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long submitAppointmentChange(
                BackendRole role, long userId, Long doctorId, long appointmentId, String kind,
                String details, java.time.LocalDate proposedDate, Integer proposedSlot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void decideAppointmentChange(
                long receptionistId, long requestId, boolean approved, String resolution) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<clyvr.backend.database.BackendNotification> findNotifications(
                long recipientId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markNotificationRead(long recipientId, long notificationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void notifyUser(long senderId, long appointmentId, String message) {
            throw new UnsupportedOperationException();
        }
    }
}
