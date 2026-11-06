package clyvr.backend.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import clyvr.backend.database.BackendAvailability;
import clyvr.backend.database.BackendConsultationRequest;
import clyvr.backend.database.BackendAppointment;
import clyvr.backend.database.BackendDoctorEntry;
import clyvr.backend.database.BackendPatientProfile;
import clyvr.backend.database.ClyvrDataRepository;
import clyvr.backend.database.AppointmentHold;
import clyvr.backend.database.PatientProfileUpdate;
import clyvr.backend.database.DoctorProposalSubmission;
import clyvr.backend.database.PatientProposalDecision;
import clyvr.backend.database.ResolvedDatabaseIdentity;
import clyvr.backend.identity.BackendRole;
import clyvr.backend.identity.DemoIdentityMappings;
import clyvr.backend.identity.DevelopmentIdentityService;
import clyvr.backend.security.DatabaseSessionService;
import clyvr.backend.security.InvalidSessionException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseControllerTest {

    private MockMvc mockMvc;
    private TestRepository repository;
    private DatabaseSessionService sessions;

    @BeforeEach
    void setUp() {
        repository = new TestRepository();
        DemoIdentityMappings mappings = new DemoIdentityMappings(Map.of(
                "CLYVR_DEMO_USER_ID_U2", "402",
                "CLYVR_DEMO_USER_ID_U3", "403",
                "CLYVR_DEMO_USER_ID_U7", "407",
                "CLYVR_DEMO_USER_ID_U8", "408",
                "CLYVR_DEMO_DOCTOR_ID_D1", "701"));
        sessions = mock(DatabaseSessionService.class);
        try {
            when(sessions.requireIdentity("U2")).thenReturn(
                    new ResolvedDatabaseIdentity(402, BackendRole.PATIENT, null));
            when(sessions.requireIdentity("U3")).thenReturn(
                    new ResolvedDatabaseIdentity(403, BackendRole.DOCTOR, 701L));
            when(sessions.requireIdentity("U7")).thenReturn(
                    new ResolvedDatabaseIdentity(407, BackendRole.RECEPTIONIST, null));
            when(sessions.requireIdentity("U8")).thenReturn(
                    new ResolvedDatabaseIdentity(408, BackendRole.ADMIN, null));
            when(sessions.requireIdentity("402")).thenThrow(new InvalidSessionException());
            when(sessions.requireIdentity("D1")).thenThrow(new InvalidSessionException());
            when(sessions.requireIdentity(null)).thenThrow(new InvalidSessionException());
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DatabaseController(sessions, mappings, repository))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void allowsDatabaseRoleReceptionistToReadPendingQueue() throws Exception {
        mockMvc.perform(get("/api/v1/receptionist/consultations/pending")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"appointmentId\":9001")));
    }

    @Test
    void deniesPatientAccessToReceptionistQueue() throws Exception {
        mockMvc.perform(get("/api/v1/receptionist/consultations/pending")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingIdentityAndInvalidIdentityKey() throws Exception {
        mockMvc.perform(get("/api/v1/doctors"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/doctors")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "402"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void availabilityUsesServerMappedDoctorKeyAndPatientUserId() throws Exception {
        mockMvc.perform(get("/api/v1/patient/availability")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .param("doctorKey", "D1")
                        .param("date", "2026-11-09"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"doctorId\":701")));
    }

    @Test
    void doctorAvailabilityUsesTheResolvedDoctorIdAndRejectsOtherRoles() throws Exception {
        mockMvc.perform(get("/api/v1/doctor/availability")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U3")
                        .param("date", "2026-11-09"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"doctorId\":701")));
        org.junit.jupiter.api.Assertions.assertEquals(701, repository.lastAvailabilityDoctorId);

        mockMvc.perform(get("/api/v1/doctor/availability")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .param("date", "2026-11-09"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnknownDoctorMappingWithoutLeakingConfiguration() throws Exception {
        mockMvc.perform(get("/api/v1/patient/availability")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .param("doctorKey", "D4")
                        .param("date", "2026-11-09"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("CLYVR_DEMO_DOCTOR_ID_D4"))));
    }

    @Test
    void allowsPatientConsultationSubmissionAndReturnsGeneratedId() throws Exception {
        mockMvc.perform(post("/api/v1/patient/consultations")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("""
                            {
                              "doctorKey": "D1",
                              "appointmentDate": "2026-11-09",
                              "slotNumber": 0,
                              "reason": "Synthetic test request"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"appointmentId\":9002")));
    }

    @Test
    void holdRoutesArePatientScopedAndReturnHoldAndAppointmentIds() throws Exception {
        mockMvc.perform(post("/api/v1/patient/appointment-holds")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("""
                            {
                              "doctorKey": "D1",
                              "appointmentDate": "2026-11-09",
                              "slotNumber": 0
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"holdId\":9901")))
                .andExpect(content().string(containsString("\"expiresAt\"")));
        org.junit.jupiter.api.Assertions.assertEquals(402, repository.lastHoldPatientId);
        org.junit.jupiter.api.Assertions.assertEquals(701, repository.lastHoldDoctorId);

        mockMvc.perform(post("/api/v1/patient/appointment-holds/9901/confirm")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("{\"reason\":\"Synthetic booking\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"appointmentId\":9002")));
        org.junit.jupiter.api.Assertions.assertEquals(402, repository.lastHoldPatientId);
        org.junit.jupiter.api.Assertions.assertEquals("Synthetic booking", repository.lastHoldReason);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/patient/appointment-holds/9901")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"released\":true")));

        mockMvc.perform(post("/api/v1/patient/appointment-holds")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U3")
                        .contentType("application/json")
                        .content("""
                            {"doctorKey":"D1","appointmentDate":"2026-11-09","slotNumber":0}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorProposalUsesResolvedDoctorAndPatientDecisionUsesResolvedPatient() throws Exception {
        mockMvc.perform(post("/api/v1/doctor/proposals")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U3")
                        .contentType("application/json")
                        .content("""
                            {
                              "patientId": 402,
                              "appointmentType": "FOLLOW_UP",
                              "appointmentDate": "2026-11-09",
                              "slotNumber": 0,
                              "reason": "Synthetic follow-up"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"appointmentId\":9004")));
        org.junit.jupiter.api.Assertions.assertEquals(701, repository.lastProposalDoctorId);
        org.junit.jupiter.api.Assertions.assertEquals(402, repository.lastProposalPatientId);

        mockMvc.perform(post("/api/v1/doctor/proposals")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("""
                            {
                              "patientId": 402,
                              "appointmentType": "PROCEDURE",
                              "appointmentDate": "2026-11-09",
                              "slotNumber": 1,
                              "reason": "Synthetic procedure"
                            }
                            """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/patient/appointments/9004/proposal-decision")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"CONFIRMED\"")));
        org.junit.jupiter.api.Assertions.assertEquals(402, repository.lastProposalDecisionPatientId);
        org.junit.jupiter.api.Assertions.assertEquals(9004, repository.lastProposalDecisionAppointmentId);

        mockMvc.perform(post("/api/v1/patient/appointments/9004/proposal-decision")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U7")
                        .contentType("application/json")
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/patient/appointments/9004/proposal-decision")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U7")
                        .contentType("application/json")
                        .content("{\"approved\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void proposalDeclineReturnsDeclinedStatusForTheResolvedPatient() throws Exception {
        mockMvc.perform(post("/api/v1/patient/appointments/9004/proposal-decision")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("{\"approved\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"DECLINED\"")));
        org.junit.jupiter.api.Assertions.assertEquals(402, repository.lastProposalDecisionPatientId);
        org.junit.jupiter.api.Assertions.assertEquals(9004, repository.lastProposalDecisionAppointmentId);
    }

    @Test
    void deniesReceptionistFromPatientConsultationSubmission() throws Exception {
        mockMvc.perform(post("/api/v1/patient/consultations")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U7")
                        .contentType("application/json")
                        .content("""
                            {
                              "doctorKey": "D1",
                              "appointmentDate": "2026-11-09",
                              "slotNumber": 0,
                              "reason": "Synthetic test request"
                            }
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCanReadOwnProfileWithoutReturningAuthenticationSecrets() throws Exception {
        mockMvc.perform(get("/api/v1/patient/profile")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"userId\":402")))
                .andExpect(content().string(not(containsString("password_hash"))));
    }

    @Test
    void profileUpdateUsesResolvedIdentityAndIgnoresClientSuppliedId() throws Exception {
        mockMvc.perform(put("/api/v1/patient/profile")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("""
                            {
                              "userId": 999,
                              "firstName": "Updated",
                              "lastName": "Patient",
                              "phone": "555-0101",
                              "birthDate": "1990-01-01",
                              "sex": "Other",
                              "address": "Synthetic address",
                              "bloodType": "Unknown",
                              "allergies": "",
                              "conditions": "",
                              "medications": "",
                              "medicalHistory": ""
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"userId\":402")));
        org.junit.jupiter.api.Assertions.assertEquals(402, repository.updatedProfileUserId);
    }

    @Test
    void missingAndDuplicateProfilesReturnExplicitStatuses() throws Exception {
        repository.profileMissing = true;
        mockMvc.perform(get("/api/v1/patient/profile")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2"))
                .andExpect(status().isNotFound());
        repository.profileMissing = false;
        repository.profileDuplicate = true;
        mockMvc.perform(get("/api/v1/patient/profile")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2"))
                .andExpect(status().isConflict());
        repository.profileDuplicate = false;
        repository.failDatabase = true;
        mockMvc.perform(get("/api/v1/patient/profile")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("password_hash"))));
    }

    @Test
    void appointmentListsUseTheResolvedPatientOrDoctorIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/appointments")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"appointmentId\":9001")));
        org.junit.jupiter.api.Assertions.assertEquals(BackendRole.PATIENT, repository.lastRole);
        org.junit.jupiter.api.Assertions.assertEquals(402, repository.lastUserId);

        mockMvc.perform(get("/api/v1/appointments")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U3"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(BackendRole.DOCTOR, repository.lastRole);
        org.junit.jupiter.api.Assertions.assertEquals(701, repository.lastDoctorId);
    }

    @Test
    void appointmentDetailsCannotBeReadOutsideResolvedOwnership() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/9001")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/appointments/9002")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/appointments")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U7"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/appointments")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U8"))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionistDecisionsAreRoleProtectedAndInvalidInputsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/receptionist/appointments/9001/decision")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U7")
                        .contentType("application/json")
                        .content("{\"approved\":false,\"reason\":\"Duplicate request\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"REJECTED\"")))
                .andExpect(content().string(containsString("\"rejectionExplanationPersisted\":false")));
        mockMvc.perform(post("/api/v1/receptionist/appointments/9001/decision")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("{\"approved\":true,\"reason\":\"\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/receptionist/appointments/9001/decision")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U7")
                        .contentType("application/json")
                        .content("{\"approved\":false,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusEndpointsOnlyAcceptRoleSpecificValues() throws Exception {
        mockMvc.perform(put("/api/v1/patient/appointments/9001/status")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/doctor/appointments/9001/status")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U3")
                        .contentType("application/json")
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/doctor/appointments/9001/status")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/patient/profile")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("""
                            {
                              "firstName": "Test",
                              "lastName": "Patient",
                              "phone": "",
                              "birthDate": "not-a-date",
                              "sex": "",
                              "address": "",
                              "bloodType": "",
                              "allergies": "",
                              "conditions": "",
                              "medications": "",
                              "medicalHistory": ""
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timeOffRoutesUseDoctorAndReceptionistRolesAndResolvedIds() throws Exception {
        mockMvc.perform(post("/api/v1/doctor/time-off")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U3")
                        .contentType("application/json")
                        .content("""
                            {"requestDate":"2026-11-09","slotNumber":0,"reason":"Synthetic"}
                            """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"timeOffId\":3002")));
        org.junit.jupiter.api.Assertions.assertEquals(701, repository.lastAvailabilityDoctorId);

        mockMvc.perform(post("/api/v1/doctor/time-off")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("""
                            {"requestDate":"2026-11-09","slotNumber":0,"reason":"Synthetic"}
                            """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/receptionist/time-off/pending")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"timeOffId\":3001")));
        mockMvc.perform(post("/api/v1/receptionist/time-off/3001/decision")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U7")
                        .contentType("application/json")
                        .content("{\"approved\":true,\"reason\":\"Approved\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"approved\"")));
        org.junit.jupiter.api.Assertions.assertEquals(407, repository.lastReviewerId);
    }

    @Test
    void appointmentChangeSubmissionAndDecisionsAreRoleScoped() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/9001/changes")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2")
                        .contentType("application/json")
                        .content("""
                            {"appointmentId":9001,"kind":"cancellation","details":"Synthetic reason"}
                            """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"requestId\":4001")));
        org.junit.jupiter.api.Assertions.assertEquals(BackendRole.PATIENT, repository.lastRole);
        org.junit.jupiter.api.Assertions.assertEquals(402, repository.lastUserId);
        org.junit.jupiter.api.Assertions.assertNull(repository.lastDoctorId);

        mockMvc.perform(post("/api/v1/appointments/9001/changes")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U7")
                        .contentType("application/json")
                        .content("""
                            {"appointmentId":9001,"kind":"cancellation","details":"No"}
                            """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/receptionist/appointment-changes/4100/decision")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U7")
                        .contentType("application/json")
                        .content("{\"approved\":false,\"resolution\":\"Synthetic rejection\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"rejected\"")));
        org.junit.jupiter.api.Assertions.assertEquals(407, repository.lastReviewerId);
    }

    @Test
    void notificationsAreScopedToTheResolvedIdentityAndDoctorCanNotifyOwnedPatient() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(402, repository.lastNotificationRecipient);

        mockMvc.perform(put("/api/v1/notifications/8001/read")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"isRead\":true")));
        org.junit.jupiter.api.Assertions.assertEquals(402, repository.lastNotificationRecipient);

        mockMvc.perform(post("/api/v1/doctor/appointments/9001/notify-patient")
                        .header(DatabaseController.DEMO_IDENTITY_HEADER, "U3")
                        .contentType("application/json")
                        .content("{\"message\":\"Please discuss your appointment.\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"created\":true")));
        org.junit.jupiter.api.Assertions.assertEquals(701, repository.lastNotificationSender);
    }

    private static final class TestRepository implements ClyvrDataRepository {
        private boolean profileMissing;
        private boolean profileDuplicate;
        private boolean failDatabase;
        private long updatedProfileUserId;
        private BackendRole lastRole;
        private long lastUserId;
        private Long lastDoctorId;
        private Long lastAvailabilityDoctorId;
        private long lastTimeOffDoctorId;
        private long lastReviewerId;
        private long lastNotificationRecipient;
        private long lastNotificationSender;
        private long lastProposalDoctorId;
        private long lastProposalPatientId;
        private long lastHoldPatientId;
        private long lastHoldDoctorId;
        private String lastHoldReason;
        private long lastProposalDecisionPatientId;
        private long lastProposalDecisionAppointmentId;

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
                String phone, LocalDate birthDate, String sex, String address) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updatePasswordHash(long userId, String passwordHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ResolvedDatabaseIdentity> findActiveIdentity(long userId) {
            if (userId == 407) {
                return Optional.of(new ResolvedDatabaseIdentity(
                        userId, BackendRole.RECEPTIONIST, null));
            }
            if (userId == 402) {
                return Optional.of(new ResolvedDatabaseIdentity(userId, BackendRole.PATIENT, null));
            }
            if (userId == 403) {
                return Optional.of(new ResolvedDatabaseIdentity(userId, BackendRole.DOCTOR, 701L));
            }
            if (userId == 408) {
                return Optional.of(new ResolvedDatabaseIdentity(userId, BackendRole.ADMIN, null));
            }
            return Optional.empty();
        }

        @Override
        public List<BackendDoctorEntry> findMappedDoctors(Map<String, Long> mappedDoctorIds) {
            return List.of(new BackendDoctorEntry("D1", 701, 501, "Ada", "Test", "General Medicine"));
        }

        @Override
        public BackendAvailability findAvailability(long patientId, long doctorId, LocalDate day) {
            List<BackendAvailability.Slot> slots = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> new BackendAvailability.Slot(index, "AVAILABLE"))
                    .toList();
            return new BackendAvailability(doctorId, slots);
        }

        @Override
        public BackendAvailability findDoctorAvailability(long doctorId, LocalDate day) {
            lastAvailabilityDoctorId = doctorId;
            List<BackendAvailability.Slot> slots = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> new BackendAvailability.Slot(index, "AVAILABLE"))
                    .toList();
            return new BackendAvailability(doctorId, slots);
        }

        @Override
        public List<BackendConsultationRequest> findPendingConsultations() {
            return List.of(new BackendConsultationRequest(
                    9001, 402, 701, LocalDate.of(2026, 11, 9), 0, "synthetic request"));
        }

        @Override
        public long insertConsultation(
                long patientId, long doctorId, LocalDate day, int slot, String reason) {
            return 9002;
        }

        @Override
        public AppointmentHold createAppointmentHold(
                long patientId, long doctorId, LocalDate day, int slot) {
            lastHoldPatientId = patientId;
            lastHoldDoctorId = doctorId;
            return new AppointmentHold(
                    9901, OffsetDateTime.parse("2026-11-06T12:05:00Z"));
        }

        @Override
        public long confirmAppointmentHold(long patientId, long holdId, String reason) {
            lastHoldPatientId = patientId;
            lastHoldReason = reason;
            return 9002;
        }

        @Override
        public boolean releaseAppointmentHold(long patientId, long holdId) {
            lastHoldPatientId = patientId;
            return true;
        }

        @Override
        public long createDoctorProposal(
                long doctorId, long patientId, String appointmentType,
                LocalDate day, int slot, String reason) {
            lastProposalDoctorId = doctorId;
            lastProposalPatientId = patientId;
            return 9004;
        }

        @Override
        public void decideDoctorProposal(
                long patientId, long appointmentId, boolean approved,
                LocalDate alternativeDate, Integer alternativeSlot) {
            lastProposalDecisionPatientId = patientId;
            lastProposalDecisionAppointmentId = appointmentId;
        }

        @Override
        public Optional<BackendPatientProfile> findPatientProfile(long userId) throws SQLException {
            if (failDatabase) {
                throw new SQLException("synthetic database failure");
            }
            if (profileMissing) {
                return Optional.empty();
            }
            if (profileDuplicate) {
                throw new clyvr.backend.database.AmbiguousPatientProfileException();
            }
            return Optional.of(new BackendPatientProfile(
                    userId, "Test", "Patient", "test@example.org", "555-0100",
                    "1990-01-01", "Other", "Test address", "Unknown", "", "", "", ""));
        }

        @Override
        public BackendPatientProfile updatePatientProfile(
                long userId, PatientProfileUpdate update) {
            updatedProfileUserId = userId;
            return new BackendPatientProfile(
                    userId, update.firstName(), update.lastName(), "test@example.org",
                    update.phone(), update.birthDate(), update.sex(), update.address(),
                    update.bloodType(), update.allergies(), update.conditions(),
                    update.medications(), update.medicalHistory());
        }

        @Override
        public List<BackendAppointment> findAppointments(
                BackendRole role, long userId, Long doctorId) {
            lastRole = role;
            lastUserId = userId;
            lastDoctorId = doctorId;
            return List.of(testAppointment());
        }

        @Override
        public Optional<BackendAppointment> findAppointment(
                BackendRole role, long userId, Long doctorId, long appointmentId) {
            lastRole = role;
            lastUserId = userId;
            lastDoctorId = doctorId;
            return appointmentId == 9001 ? Optional.of(testAppointment()) : Optional.empty();
        }

        @Override
        public void decidePendingConsultation(long appointmentId, boolean approved) {
        }

        @Override
        public void cancelPatientAppointment(long userId, long appointmentId) {
        }

        @Override
        public void finishDoctorAppointment(long doctorId, long appointmentId, String newStatus) {
        }

        @Override
        public List<clyvr.backend.database.BackendTimeOffRequest> findDoctorTimeOff(long doctorId) {
            lastTimeOffDoctorId = doctorId;
            return List.of();
        }

        @Override
        public List<clyvr.backend.database.BackendTimeOffRequest> findPendingTimeOff() {
            return List.of(new clyvr.backend.database.BackendTimeOffRequest(
                    3001, 701, LocalDate.of(2026, 11, 9), 0, "pending", "synthetic"));
        }

        @Override
        public long submitTimeOff(long doctorId, LocalDate day, int slot, String reason) {
            lastAvailabilityDoctorId = doctorId;
            return 3002;
        }

        @Override
        public void decideTimeOff(
                long receptionistId, long timeOffId, boolean approved, String reason) {
            lastReviewerId = receptionistId;
        }

        @Override
        public List<clyvr.backend.database.BackendAppointmentChangeRequest>
                findPendingAppointmentChanges() {
            return List.of();
        }

        @Override
        public long submitAppointmentChange(
                BackendRole role, long userId, Long doctorId, long appointmentId, String kind,
                String details, LocalDate proposedDate, Integer proposedSlot) {
            lastRole = role;
            lastUserId = userId;
            lastDoctorId = doctorId;
            return 4001;
        }

        @Override
        public void decideAppointmentChange(
                long receptionistId, long requestId, boolean approved, String resolution) {
            lastReviewerId = receptionistId;
        }

        @Override
        public List<clyvr.backend.database.BackendNotification> findNotifications(long recipientId) {
            lastNotificationRecipient = recipientId;
            return List.of();
        }

        @Override
        public void markNotificationRead(long recipientId, long notificationId) {
            lastNotificationRecipient = recipientId;
        }

        @Override
        public void notifyUser(long senderId, long appointmentId, String message) {
            lastNotificationSender = senderId;
        }

        private static BackendAppointment testAppointment() {
            return new BackendAppointment(
                    9001, 402, 701, "CONSULTATION", LocalDate.of(2026, 11, 9), 0,
                    "PENDING_RECEPTIONIST", "synthetic request", "Test", "Patient",
                    "Ada", "Test", "General Medicine");
        }
    }
}
