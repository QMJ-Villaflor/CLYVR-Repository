package clyvr.backend.database;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import clyvr.backend.identity.BackendRole;
import clyvr.backend.security.UserCredential;

public interface ClyvrDataRepository {
    Optional<UserCredential> findCredential(String login) throws SQLException;

    Optional<UserCredential> findCredential(long userId) throws SQLException;

    long registerPatient(
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            String phone,
            LocalDate birthDate,
            String sex,
            String address) throws SQLException;

    void updatePasswordHash(long userId, String passwordHash) throws SQLException;

    Optional<ResolvedDatabaseIdentity> findActiveIdentity(long userId) throws SQLException;

    List<BackendDoctorEntry> findMappedDoctors(Map<String, Long> mappedDoctorIds)
            throws SQLException;

    BackendAvailability findAvailability(long patientId, long doctorId, LocalDate day)
            throws SQLException;

    BackendAvailability findDoctorAvailability(long doctorId, LocalDate day)
            throws SQLException;

    List<BackendConsultationRequest> findPendingConsultations() throws SQLException;

    long insertConsultation(
            long patientId,
            long doctorId,
            LocalDate day,
            int slot,
            String reason) throws SQLException;

    AppointmentHold createAppointmentHold(
            long patientId, long doctorId, LocalDate day, int slot) throws SQLException;

    long confirmAppointmentHold(long patientId, long holdId, String reason) throws SQLException;

    boolean releaseAppointmentHold(long patientId, long holdId) throws SQLException;

    long createDoctorProposal(
            long doctorId,
            long patientId,
            String appointmentType,
            LocalDate day,
            int slot,
            String reason) throws SQLException;

    void decideDoctorProposal(
            long patientId,
            long appointmentId,
            boolean approved,
            LocalDate alternativeDate,
            Integer alternativeSlot) throws SQLException;

    Optional<BackendPatientProfile> findPatientProfile(long userId) throws SQLException;

    BackendPatientProfile updatePatientProfile(long userId, PatientProfileUpdate update)
            throws SQLException;

    List<BackendAppointment> findAppointments(
            BackendRole role, long userId, Long doctorId) throws SQLException;

    Optional<BackendAppointment> findAppointment(
            BackendRole role, long userId, Long doctorId, long appointmentId) throws SQLException;

    void decidePendingConsultation(long appointmentId, boolean approved) throws SQLException;

    void cancelPatientAppointment(long userId, long appointmentId) throws SQLException;

    void finishDoctorAppointment(long doctorId, long appointmentId, String newStatus)
            throws SQLException;

    List<BackendTimeOffRequest> findDoctorTimeOff(long doctorId) throws SQLException;

    List<BackendTimeOffRequest> findPendingTimeOff() throws SQLException;

    long submitTimeOff(long doctorId, LocalDate day, int slot, String reason)
            throws SQLException;

    void decideTimeOff(long receptionistId, long timeOffId, boolean approved, String reason)
            throws SQLException;

    List<BackendAppointmentChangeRequest> findPendingAppointmentChanges() throws SQLException;

    long submitAppointmentChange(
            BackendRole role,
            long userId,
            Long doctorId,
            long appointmentId,
            String kind,
            String details,
            LocalDate proposedDate,
            Integer proposedSlot) throws SQLException;

    void decideAppointmentChange(
            long receptionistId, long requestId, boolean approved, String resolution)
            throws SQLException;

    List<BackendNotification> findNotifications(long recipientId) throws SQLException;

    void markNotificationRead(long recipientId, long notificationId) throws SQLException;

    void notifyUser(long senderId, long appointmentId, String message) throws SQLException;
}
