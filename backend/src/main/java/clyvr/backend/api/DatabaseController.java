package clyvr.backend.api;

import clyvr.backend.database.BackendAppointment;
import clyvr.backend.database.BackendAvailability;
import clyvr.backend.database.BackendConsultationRequest;
import clyvr.backend.database.BackendDoctorEntry;
import clyvr.backend.database.BackendPatientProfile;
import clyvr.backend.database.AppointmentDecision;
import clyvr.backend.database.AppointmentNotFoundException;
import clyvr.backend.database.ClyvrDataRepository;
import clyvr.backend.database.PatientProfileNotFoundException;
import clyvr.backend.database.PatientProfileUpdate;
import clyvr.backend.database.ConsultationSubmission;
import clyvr.backend.database.TimeOffSubmission;
import clyvr.backend.database.TimeOffDecision;
import clyvr.backend.database.BackendTimeOffRequest;
import clyvr.backend.database.AppointmentChangeSubmission;
import clyvr.backend.database.AppointmentChangeDecision;
import clyvr.backend.database.BackendAppointmentChangeRequest;
import clyvr.backend.database.BackendNotification;
import clyvr.backend.database.AppointmentHold;
import clyvr.backend.database.AppointmentHoldSubmission;
import clyvr.backend.database.AppointmentHoldConfirmation;
import clyvr.backend.database.DoctorProposalSubmission;
import clyvr.backend.database.PatientProposalDecision;
import clyvr.backend.identity.BackendRole;
import clyvr.backend.database.ResolvedDatabaseIdentity;
import clyvr.backend.identity.DemoIdentityMappings;
import clyvr.backend.security.DatabaseSessionService;
import clyvr.backend.security.ForbiddenRoleException;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public final class DatabaseController {

    public static final String DEMO_IDENTITY_HEADER = "Authorization";

    private final DatabaseSessionService sessions;
    private final DemoIdentityMappings doctorMappings;
    private final ClyvrDataRepository repository;

    public DatabaseController(
            DatabaseSessionService sessions,
            DemoIdentityMappings doctorMappings,
            ClyvrDataRepository repository) {
        this.sessions = sessions;
        this.doctorMappings = doctorMappings;
        this.repository = repository;
    }

    @GetMapping("/doctors")
    public List<BackendDoctorEntry> doctors(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            HttpServletRequest request) throws SQLException {
        identity(demoKey, request);
        return repository.findMappedDoctors(doctorMappings.configuredDoctorIds());
    }

    @GetMapping("/patient/availability")
    public BackendAvailability availability(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @RequestParam("doctorKey") String doctorKey,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.PATIENT);
        long doctorId = doctorMappings.doctorId(doctorKey);
        return repository.findAvailability(identity.userId(), doctorId, day);
    }

    @GetMapping("/doctor/availability")
    public BackendAvailability doctorAvailability(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.DOCTOR);
        return repository.findDoctorAvailability(identity.doctorId(), day);
    }

    @GetMapping("/receptionist/consultations/pending")
    public List<BackendConsultationRequest> pendingConsultations(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            HttpServletRequest request) throws SQLException {
        requireRole(demoKey, request, BackendRole.RECEPTIONIST);
        return repository.findPendingConsultations();
    }

    @PostMapping("/patient/consultations")
    public ConsultationCreated createConsultation(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @RequestBody ConsultationSubmission submission,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.PATIENT);
        long doctorId = doctorMappings.doctorId(submission.doctorKey());
        long appointmentId = repository.insertConsultation(
                identity.userId(), doctorId, submission.appointmentDate(),
                submission.slotNumber(), submission.reason());
        return new ConsultationCreated(appointmentId);
    }

    @PostMapping("/patient/appointment-holds")
    public HoldCreated createAppointmentHold(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @RequestBody AppointmentHoldSubmission submission,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.PATIENT);
        if (submission == null) {
            throw new IllegalArgumentException("Appointment hold request is required.");
        }
        long doctorId = doctorMappings.doctorId(submission.doctorKey());
        AppointmentHold hold = repository.createAppointmentHold(
                identity.userId(), doctorId, submission.appointmentDate(),
                submission.slotNumber());
        return new HoldCreated(hold.holdId(), hold.expiresAt());
    }

    @PostMapping("/patient/appointment-holds/{holdId}/confirm")
    public ConsultationCreated confirmAppointmentHold(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @PathVariable long holdId,
            @RequestBody AppointmentHoldConfirmation confirmation,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.PATIENT);
        if (holdId <= 0 || confirmation == null) {
            throw new IllegalArgumentException("Appointment hold confirmation is invalid.");
        }
        return new ConsultationCreated(repository.confirmAppointmentHold(
                identity.userId(), holdId, confirmation.reason()));
    }

    @DeleteMapping("/patient/appointment-holds/{holdId}")
    public HoldReleased releaseAppointmentHold(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @PathVariable long holdId,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.PATIENT);
        if (holdId <= 0) {
            throw new IllegalArgumentException("Appointment hold ID is invalid.");
        }
        return new HoldReleased(repository.releaseAppointmentHold(identity.userId(), holdId));
    }

    @PostMapping("/doctor/proposals")
    public AppointmentCreated createDoctorProposal(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @RequestBody DoctorProposalSubmission submission,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.DOCTOR);
        if (submission == null) {
            throw new IllegalArgumentException("Doctor proposal is required.");
        }
        long appointmentId = repository.createDoctorProposal(
                identity.doctorId(), submission.patientId(), submission.appointmentType(),
                submission.appointmentDate(), submission.slotNumber(), submission.reason());
        return new AppointmentCreated(appointmentId);
    }

    @PostMapping("/patient/appointments/{appointmentId}/proposal-decision")
    public AppointmentStatusResult decideDoctorProposal(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @PathVariable long appointmentId,
            @RequestBody PatientProposalDecision decision,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.PATIENT);
        if (appointmentId <= 0 || decision == null
                || (!decision.approved()
                    && (decision.appointmentDate() != null || decision.slotNumber() != null))) {
            throw new IllegalArgumentException("Proposal decision is invalid.");
        }
        repository.decideDoctorProposal(identity.userId(), appointmentId, decision.approved(),
                decision.appointmentDate(), decision.slotNumber());
        return new AppointmentStatusResult(decision.approved() ? "CONFIRMED" : "DECLINED");
    }

    @GetMapping("/patient/profile")
    public BackendPatientProfile patientProfile(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.PATIENT);
        return repository.findPatientProfile(identity.userId())
                .orElseThrow(PatientProfileNotFoundException::new);
    }

    @PutMapping("/patient/profile")
    public BackendPatientProfile updatePatientProfile(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @RequestBody PatientProfileUpdate update,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.PATIENT);
        return repository.updatePatientProfile(identity.userId(), update);
    }

    @GetMapping("/appointments")
    public List<BackendAppointment> appointments(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = identity(demoKey, request);
        if (identity.role() == BackendRole.ADMIN) {
            throw new ForbiddenRoleException();
        }
        return repository.findAppointments(
                identity.role(), identity.userId(), identity.doctorId());
    }

    @GetMapping("/appointments/{appointmentId}")
    public BackendAppointment appointmentDetails(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @PathVariable long appointmentId,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = identity(demoKey, request);
        if (identity.role() == BackendRole.ADMIN) {
            throw new ForbiddenRoleException();
        }
        return repository.findAppointment(
                        identity.role(), identity.userId(), identity.doctorId(), appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);
    }

    @PostMapping("/receptionist/appointments/{appointmentId}/decision")
    public AppointmentDecisionResult decideConsultation(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @PathVariable long appointmentId,
            @RequestBody AppointmentDecision decision,
            HttpServletRequest request) throws SQLException {
        requireRole(demoKey, request, BackendRole.RECEPTIONIST);
        if (appointmentId <= 0) {
            throw new IllegalArgumentException("Appointment ID must be positive.");
        }
        repository.decidePendingConsultation(appointmentId, decision.approved());
        return new AppointmentDecisionResult(
                decision.approved() ? "CONFIRMED" : "REJECTED", false);
    }

    @PutMapping("/patient/appointments/{appointmentId}/status")
    public AppointmentStatusResult cancelPatientAppointment(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @PathVariable long appointmentId,
            @RequestBody AppointmentStatusUpdate update,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.PATIENT);
        if (update == null || !"CANCELLED".equals(update.status())) {
            throw new IllegalArgumentException("Patients may only cancel their own appointment.");
        }
        repository.cancelPatientAppointment(identity.userId(), appointmentId);
        return new AppointmentStatusResult("CANCELLED");
    }

    @PutMapping("/doctor/appointments/{appointmentId}/status")
    public AppointmentStatusResult finishDoctorAppointment(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @PathVariable long appointmentId,
            @RequestBody AppointmentStatusUpdate update,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.DOCTOR);
        if (update == null) {
            throw new IllegalArgumentException("The requested doctor status is not supported.");
        }
        String status = update.status();
        if (!"COMPLETED".equals(status) && !"NO_SHOW".equals(status)) {
            throw new IllegalArgumentException("The requested doctor status is not supported.");
        }
        repository.finishDoctorAppointment(identity.doctorId(), appointmentId, status);
        return new AppointmentStatusResult(status);
    }

    @GetMapping("/doctor/time-off")
    public List<BackendTimeOffRequest> doctorTimeOff(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.DOCTOR);
        return repository.findDoctorTimeOff(identity.doctorId());
    }

    @PostMapping("/doctor/time-off")
    public TimeOffCreated submitTimeOff(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @RequestBody TimeOffSubmission submission,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.DOCTOR);
        if (submission == null) {
            throw new IllegalArgumentException("Time-off request is required.");
        }
        return new TimeOffCreated(repository.submitTimeOff(
                identity.doctorId(), submission.requestDate(),
                submission.slotNumber(), submission.reason()));
    }

    @GetMapping("/receptionist/time-off/pending")
    public List<BackendTimeOffRequest> pendingTimeOff(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            HttpServletRequest request) throws SQLException {
        requireRole(demoKey, request, BackendRole.RECEPTIONIST);
        return repository.findPendingTimeOff();
    }

    @PostMapping("/receptionist/time-off/{timeOffId}/decision")
    public DecisionSaved decideTimeOff(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @PathVariable long timeOffId,
            @RequestBody TimeOffDecision decision,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.RECEPTIONIST);
        if (timeOffId <= 0 || decision == null || decision.reason() == null
                || decision.reason().length() > 2000) {
            throw new IllegalArgumentException("Time-off decision is invalid.");
        }
        repository.decideTimeOff(
                identity.userId(), timeOffId, decision.approved(), decision.reason());
        return new DecisionSaved(decision.approved() ? "approved" : "rejected");
    }

    @GetMapping("/receptionist/appointment-changes/pending")
    public List<BackendAppointmentChangeRequest> pendingAppointmentChanges(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            HttpServletRequest request) throws SQLException {
        requireRole(demoKey, request, BackendRole.RECEPTIONIST);
        return repository.findPendingAppointmentChanges();
    }

    @PostMapping("/appointments/{appointmentId}/changes")
    public ChangeCreated submitAppointmentChange(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @PathVariable long appointmentId,
            @RequestBody AppointmentChangeSubmission submission,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = identity(demoKey, request);
        if (identity.role() != BackendRole.PATIENT && identity.role() != BackendRole.DOCTOR) {
            throw new ForbiddenRoleException();
        }
        if (appointmentId <= 0 || submission == null
                || submission.appointmentId() != appointmentId) {
            throw new IllegalArgumentException("Appointment-change request is invalid.");
        }
        long requestId = repository.submitAppointmentChange(
                identity.role(), identity.userId(), identity.doctorId(), appointmentId,
                submission.kind(), submission.details(),
                submission.proposedDate(), submission.proposedSlot());
        return new ChangeCreated(requestId);
    }

    @PostMapping("/receptionist/appointment-changes/{requestId}/decision")
    public DecisionSaved decideAppointmentChange(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @PathVariable long requestId,
            @RequestBody AppointmentChangeDecision decision,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.RECEPTIONIST);
        if (requestId <= 0 || decision == null || decision.resolution() == null
                || decision.resolution().length() > 2000) {
            throw new IllegalArgumentException("Appointment-change decision is invalid.");
        }
        repository.decideAppointmentChange(
                identity.userId(), requestId, decision.approved(), decision.resolution());
        return new DecisionSaved(decision.approved() ? "approved" : "rejected");
    }

    @GetMapping("/notifications")
    public List<BackendNotification> notifications(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = identity(demoKey, request);
        return repository.findNotifications(identity.userId());
    }

    @PutMapping("/notifications/{notificationId}/read")
    public NotificationReadResult markNotificationRead(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @PathVariable long notificationId,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = identity(demoKey, request);
        repository.markNotificationRead(identity.userId(), notificationId);
        return new NotificationReadResult(notificationId, true);
    }

    @PostMapping("/doctor/appointments/{appointmentId}/notify-patient")
    public NotificationCreated notifyAppointmentPatient(
            @RequestHeader(value = DEMO_IDENTITY_HEADER, required = false) String demoKey,
            @PathVariable long appointmentId,
            @RequestBody PatientNotification message,
            HttpServletRequest request) throws SQLException {
        ResolvedDatabaseIdentity identity = requireRole(demoKey, request, BackendRole.DOCTOR);
        if (appointmentId <= 0 || message == null || message.message() == null
                || message.message().isBlank() || message.message().length() > 1000) {
            throw new IllegalArgumentException("Patient notification is invalid.");
        }
        repository.notifyUser(identity.doctorId(), appointmentId, message.message());
        return new NotificationCreated(true);
    }

    private ResolvedDatabaseIdentity identity(String ignoredDemoKey, HttpServletRequest request)
            throws SQLException {
        return sessions.requireIdentity(request.getHeader("Authorization"));
    }

    private ResolvedDatabaseIdentity requireRole(
            String demoKey, HttpServletRequest request, BackendRole expectedRole)
            throws SQLException {
        ResolvedDatabaseIdentity identity = identity(demoKey, request);
        if (identity.role() != expectedRole) {
            throw new ForbiddenRoleException();
        }
        return identity;
    }

    public record ConsultationCreated(long appointmentId) {
    }

    public record HoldCreated(long holdId, java.time.OffsetDateTime expiresAt) {
    }

    public record HoldReleased(boolean released) {
    }

    public record AppointmentCreated(long appointmentId) {
    }

    public record AppointmentDecisionResult(
            String status, boolean rejectionExplanationPersisted) {
    }

    public record AppointmentStatusResult(String status) {
    }

    public record TimeOffCreated(long timeOffId) {
    }

    public record ChangeCreated(long requestId) {
    }

    public record DecisionSaved(String status) {
    }

    public record NotificationReadResult(long notificationId, boolean isRead) {
    }

    public record NotificationCreated(boolean created) {
    }

    public record PatientNotification(String message) {
    }
}
