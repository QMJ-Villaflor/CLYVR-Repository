import clyvr.service.BackendApiClient;
import clyvr.database.AvailabilitySlot;
import clyvr.database.DoctorAvailability;
import clyvr.database.DoctorDirectoryEntry;
import clyvr.database.BackendAppointment;
import clyvr.database.BackendPatientProfile;
import clyvr.database.PatientProfileUpdate;
import clyvr.database.AppointmentDecisionResult;
import clyvr.database.AppointmentHold;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public class BackendApiClientTest {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> loginBody = new AtomicReference<>();
        AtomicReference<String> consultationBody = new AtomicReference<>();
        AtomicReference<String> holdBody = new AtomicReference<>();
        AtomicReference<String> profileBody = new AtomicReference<>();
        AtomicReference<String> decisionBody = new AtomicReference<>();
        AtomicReference<String> workflowBody = new AtomicReference<>();
        server.createContext("/api/v1/auth/verify", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"uid\":\"synthetic-user\",\"verified\":true}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/api/v1/auth/login", exchange -> {
            loginBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            reply(exchange, 200, "{\"accessToken\":\"synthetic-session-token-1234567890\","
                    + "\"expiresAt\":\"2026-11-06T20:00:00Z\",\"userId\":402,"
                    + "\"role\":\"PATIENT\",\"doctorId\":null,\"firstName\":\"Test\","
                    + "\"lastName\":\"Patient\",\"email\":\"test@example.org\","
                    + "\"phone\":\"555-0100\"}");
        });
        server.createContext("/api/v1/doctors", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = ("[{\"demoDoctorKey\":\"D1\",\"doctorId\":701,\"userId\":501,"
                    + "\"firstName\":\"Ada\",\"lastName\":\"Test\","
                    + "\"specialty\":\"General Medicine\"}]")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/api/v1/patient/availability", exchange -> {
            byte[] response = ("{\"doctorId\":701,\"slots\":["
                    + "{\"slotNumber\":0,\"state\":\"AVAILABLE\"},"
                    + "{\"slotNumber\":1,\"state\":\"APPOINTMENT\"},"
                    + "{\"slotNumber\":2,\"state\":\"AVAILABLE\"},"
                    + "{\"slotNumber\":3,\"state\":\"AVAILABLE\"},"
                    + "{\"slotNumber\":4,\"state\":\"AVAILABLE\"},"
                    + "{\"slotNumber\":5,\"state\":\"AVAILABLE\"},"
                    + "{\"slotNumber\":6,\"state\":\"AVAILABLE\"},"
                    + "{\"slotNumber\":7,\"state\":\"AVAILABLE\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/api/v1/doctor/availability", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, 200, "{\"doctorId\":701,\"slots\":["
                    + "{\"slotNumber\":0,\"state\":\"AVAILABLE\"},"
                    + "{\"slotNumber\":1,\"state\":\"APPOINTMENT\"},"
                    + "{\"slotNumber\":2,\"state\":\"AVAILABLE\"},"
                    + "{\"slotNumber\":3,\"state\":\"AVAILABLE\"},"
                    + "{\"slotNumber\":4,\"state\":\"APPROVED_TIME_OFF\"},"
                    + "{\"slotNumber\":5,\"state\":\"AVAILABLE\"},"
                    + "{\"slotNumber\":6,\"state\":\"RESCHEDULE_HOLD\"},"
                    + "{\"slotNumber\":7,\"state\":\"AVAILABLE\"}]}");
        });
        server.createContext("/api/v1/patient/consultations", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            consultationBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            byte[] response = "{\"appointmentId\":9002}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/api/v1/patient/appointment-holds", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("POST".equals(exchange.getRequestMethod())) {
                holdBody.set(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                reply(exchange, 200,
                        "{\"holdId\":9901,\"expiresAt\":\"2026-11-06T12:05:00Z\"}");
            } else {
                reply(exchange, 200, "{\"released\":true}");
            }
        });
        server.createContext("/api/v1/patient/appointment-holds/9901/confirm", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            workflowBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            reply(exchange, 200, "{\"appointmentId\":9003}");
        });
        server.createContext("/api/v1/patient/profile", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("PUT".equals(exchange.getRequestMethod())) {
                profileBody.set(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
            }
            reply(exchange, 200, "{\"userId\":402,\"firstName\":\"Test\","
                    + "\"lastName\":\"Patient\",\"email\":\"test@example.org\","
                    + "\"phone\":\"555-0100\",\"birthDate\":\"1990-01-01\","
                    + "\"sex\":\"Other\",\"address\":\"Test address\","
                    + "\"bloodType\":\"Unknown\",\"allergies\":\"\","
                    + "\"conditions\":\"\",\"medications\":\"\","
                    + "\"medicalHistory\":\"\"}");
        });
        server.createContext("/api/v1/appointments", exchange -> reply(exchange, 200,
                "[{\"appointmentId\":9001,\"patientId\":402,\"doctorId\":701,"
                        + "\"appointmentType\":\"CONSULTATION\","
                        + "\"appointmentDate\":\"2026-11-09\",\"slotNumber\":0,"
                        + "\"status\":\"PENDING_RECEPTIONIST\",\"reason\":\"test\","
                        + "\"patientFirstName\":\"Test\",\"patientLastName\":\"Patient\","
                        + "\"doctorFirstName\":\"Ada\",\"doctorLastName\":\"Test\","
                        + "\"specialty\":\"General Medicine\"}]"));
        server.createContext("/api/v1/appointments/9001", exchange -> reply(exchange, 200,
                "{\"appointmentId\":9001,\"patientId\":402,\"doctorId\":701,"
                        + "\"appointmentType\":\"CONSULTATION\","
                        + "\"appointmentDate\":\"2026-11-09\",\"slotNumber\":0,"
                        + "\"status\":\"PENDING_RECEPTIONIST\",\"reason\":\"test\","
                        + "\"patientFirstName\":\"Test\",\"patientLastName\":\"Patient\","
                        + "\"doctorFirstName\":\"Ada\",\"doctorLastName\":\"Test\","
                        + "\"specialty\":\"General Medicine\"}"));
        server.createContext("/api/v1/receptionist/appointments/9001/decision", exchange -> {
            decisionBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            reply(exchange, 200,
                    "{\"status\":\"REJECTED\",\"rejectionExplanationPersisted\":false}");
        });
        server.createContext("/api/v1/patient/appointments/9001/status", exchange -> {
            exchange.getRequestBody().readAllBytes();
            reply(exchange, 200, "{\"status\":\"CANCELLED\"}");
        });
        server.createContext("/api/v1/doctor/appointments/9001/status", exchange -> {
            exchange.getRequestBody().readAllBytes();
            reply(exchange, 200, "{\"status\":\"COMPLETED\"}");
        });
        server.createContext("/api/v1/doctor/time-off", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("POST".equals(exchange.getRequestMethod())) {
                workflowBody.set(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                reply(exchange, 200, "{\"timeOffId\":7101}");
            } else {
                reply(exchange, 200, "[{\"timeOffId\":7101,\"doctorId\":701,"
                        + "\"requestDate\":\"2026-11-09\",\"slotNumber\":0,"
                        + "\"status\":\"pending\",\"reason\":\"synthetic\"}]");
            }
        });
        server.createContext("/api/v1/receptionist/time-off/pending", exchange ->
                reply(exchange, 200, "[]"));
        server.createContext("/api/v1/receptionist/time-off/7101/decision", exchange -> {
            workflowBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            reply(exchange, 200, "{\"status\":\"approved\"}");
        });
        server.createContext("/api/v1/receptionist/appointment-changes/pending", exchange ->
                reply(exchange, 200, "[{\"requestId\":8101,\"appointmentId\":9001,"
                        + "\"requesterId\":402,\"kind\":\"reschedule\",\"details\":\"synthetic\","
                        + "\"status\":\"pending\",\"proposedDate\":\"2026-11-11\","
                        + "\"proposedSlot\":2,\"appointmentDate\":\"2026-11-09\","
                        + "\"appointmentSlot\":0,\"appointmentType\":\"CONSULTATION\","
                        + "\"requesterName\":\"Test Patient\",\"patientName\":\"Test Patient\","
                        + "\"doctorName\":\"Dr. Ada Test\"}]"));
        server.createContext("/api/v1/appointments/9001/changes", exchange -> {
            workflowBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            reply(exchange, 200, "{\"requestId\":8102}");
        });
        server.createContext("/api/v1/receptionist/appointment-changes/8101/decision", exchange -> {
            workflowBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            reply(exchange, 200, "{\"status\":\"rejected\"}");
        });
        server.createContext("/api/v1/notifications", exchange -> {
            if ("PUT".equals(exchange.getRequestMethod())) {
                reply(exchange, 200, "{\"notificationId\":8201,\"isRead\":true}");
            } else {
                reply(exchange, 200, "[{\"notificationId\":8201,\"message\":\"Synthetic notice\","
                        + "\"createdAt\":\"2026-11-06T09:30:00Z\",\"isRead\":false}]");
            }
        });
        server.createContext("/api/v1/doctor/appointments/9001/notify-patient", exchange -> {
            workflowBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            reply(exchange, 200, "{\"created\":true}");
        });
        server.createContext("/api/v1/doctor/proposals", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            workflowBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            reply(exchange, 200, "{\"appointmentId\":9101}");
        });
        server.createContext("/api/v1/patient/appointments/9101/proposal-decision", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            workflowBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            if (workflowBody.get().contains("\"approved\":false")) {
                reply(exchange, 200, "{\"status\":\"DECLINED\"}");
            } else {
                reply(exchange, 200, "{\"status\":\"CONFIRMED\"}");
            }
        });
        server.start();
        try {
            URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            BackendApiClient client = new BackendApiClient(baseUri);
            HttpResponse<String> response = client.verifyIdentity("synthetic.id.token").join();
            check(response.statusCode() == 200, "verification response status");
            check(response.body().contains("synthetic-user"), "verification response body");
            check("Bearer synthetic.id.token".equals(authorization.get()),
                    "Firebase token is sent in the Authorization header");

            var account = client.login("test@example.org", "synthetic-password").join();
            check(account.userId() == 402 && account.email().equals("test@example.org"),
                    "database login response is mapped to the signed-in desktop account");
            check(loginBody.get().contains("\"password\":\"synthetic-password\"")
                            && !loginBody.get().contains("password_hash"),
                    "login sends credentials only and receives no stored password hash");

            List<DoctorDirectoryEntry> doctors = client.findDoctors("U2").join();
            check(doctors.size() == 1 && doctors.get(0).doctorId() == 701,
                    "doctor directory response mapping");
            check("Bearer synthetic-session-token-1234567890".equals(authorization.get()),
                    "protected requests use the session bearer token");

            DoctorAvailability availability =
                    client.findAvailability("U2", 701, LocalDate.of(2026, 11, 9)).join();
            check(availability.slots().get(1).state() == AvailabilitySlot.State.APPOINTMENT,
                    "availability response mapping");
            DoctorAvailability doctorAvailability = client.findDoctorAvailability(
                    "U3", LocalDate.of(2026, 11, 9)).join();
            check(doctorAvailability.doctorId() == 701
                            && doctorAvailability.slots().get(4).state()
                                    == AvailabilitySlot.State.APPROVED_TIME_OFF
                            && "Bearer synthetic-session-token-1234567890".equals(authorization.get()),
                    "doctor availability uses a role-scoped endpoint and session token");

            long appointmentId = client.createConsultation(
                    "U2", "D1", LocalDate.of(2026, 11, 9), 0, "test \"reason\"").join();
            check(appointmentId == 9002, "consultation generated ID mapping");
            check(consultationBody.get().contains("test \\\"reason\\\""),
                    "consultation reason JSON escaping");
            check("Bearer synthetic-session-token-1234567890".equals(authorization.get()),
                    "consultation uses the authenticated session");

            AppointmentHold hold = client.createAppointmentHold(
                    "U2", "D1", LocalDate.of(2026, 11, 9), 0).join();
            check(hold.holdId() == 9901
                            && hold.expiresAt().equals(
                                    OffsetDateTime.parse("2026-11-06T12:05:00Z")),
                    "appointment hold response mapping");
            check(holdBody.get().contains("\"doctorKey\":\"D1\"")
                            && holdBody.get().contains("\"slotNumber\":0")
                            && "Bearer synthetic-session-token-1234567890".equals(authorization.get()),
                    "appointment hold sends the selected slot with authenticated session");
            check(client.confirmAppointmentHold("U2", 9901, "test hold").join() == 9003
                            && workflowBody.get().contains("\"reason\":\"test hold\""),
                    "appointment hold confirmation mapping");
            check(client.releaseAppointmentHold("U2", 9901).join(),
                    "appointment hold release response mapping");

            BackendPatientProfile profile = client.findPatientProfile("U2").join();
            check(profile.userId() == 402 && profile.email().equals("test@example.org"),
                    "patient profile result mapping");
            BackendPatientProfile updatedProfile = client.updatePatientProfile("U2",
                    new PatientProfileUpdate("Taylor", "Patient", "555-0102",
                            "1990-01-01", "Other", "Test address", "Unknown",
                            "Tree \"nuts\"", "", "", "")).join();
            check(updatedProfile.firstName().equals("Test")
                            && profileBody.get().contains("Tree \\\"nuts\\\""),
                    "patient profile update mapping and JSON escaping");

            List<BackendAppointment> appointments = client.findAppointments("U2").join();
            check(appointments.size() == 1 && appointments.get(0).appointmentId() == 9001
                            && appointments.get(0).patientName().equals("Test Patient"),
                    "appointment list mapping");
            BackendAppointment appointment = client.findAppointment("U2", 9001).join();
            check(appointment.status().equals("PENDING_RECEPTIONIST"),
                    "appointment details mapping");

            AppointmentDecisionResult decision =
                    client.decideAppointment("U7", 9001, false, "Duplicate request: reviewed").join();
            check(decision.status().equals("REJECTED")
                            && !decision.rejectionExplanationPersisted()
                            && decisionBody.get().contains("Duplicate request"),
                    "receptionist decision response and limitation mapping");
            check(client.cancelPatientAppointment("U2", 9001).join().equals("CANCELLED"),
                    "patient appointment status response");
            check(client.finishDoctorAppointment("U3", 9001, "COMPLETED").join()
                            .equals("COMPLETED"),
                    "doctor appointment status response");

            check(client.submitTimeOff("U3", LocalDate.of(2026, 11, 9), 0,
                    "Synthetic leave").join() == 7101
                    && workflowBody.get().contains("\"slotNumber\":0"),
                    "time-off request HTTP mapping");
            check(client.findDoctorTimeOff("U3").join().get(0).timeOffId() == 7101,
                    "doctor time-off list response mapping");
            check(client.decideTimeOff("U7", 7101, true, "Reviewed").join()
                    .equals("approved"), "time-off decision mapping");
            check(client.findPendingTimeOff("U7").join().isEmpty(),
                    "pending time-off response mapping");
            check(client.findPendingAppointmentChanges("U7").join().get(0).requestId() == 8101,
                    "pending appointment-change response mapping");
            check(client.submitAppointmentChange("U2", 9001, "reschedule",
                    "Synthetic change", LocalDate.of(2026, 11, 11), 2).join() == 8102
                    && workflowBody.get().contains("\"proposedSlot\":2"),
                    "appointment-change request mapping");
            check(client.decideAppointmentChange("U7", 8101, false, "Reviewed").join()
                    .equals("rejected"), "appointment-change decision mapping");
            check(client.findNotifications("U2").join().get(0).notificationId() == 8201,
                    "persistent notification mapping");
            check(client.markNotificationRead("U2", 8201).join(),
                    "notification read-status mapping");
            check(client.notifyAppointmentPatient("U3", 9001, "Please call us.").join(),
                    "doctor-to-patient notification mapping");

            check(client.createDoctorProposal("U3", 402, "FOLLOW_UP",
                    LocalDate.of(2026, 11, 9), 0, "Synthetic follow-up").join() == 9101
                    && workflowBody.get().contains("\"patientId\":402")
                    && workflowBody.get().contains("\"appointmentType\":\"FOLLOW_UP\""),
                    "doctor proposal submission mapping");
            check(client.decideDoctorProposal("U2", 9101, true,
                    LocalDate.of(2026, 11, 11), 2).join().equals("CONFIRMED")
                    && workflowBody.get().contains("\"slotNumber\":2")
                    && "Bearer synthetic-session-token-1234567890".equals(authorization.get()),
                    "patient proposal approval with alternative slot mapping");
            check(client.decideDoctorProposal("U2", 9101, false, null, null).join()
                            .equals("DECLINED")
                    && workflowBody.get().contains("\"approved\":false")
                    && "Bearer synthetic-session-token-1234567890".equals(authorization.get()),
                    "patient proposal decline uses the identity-scoped backend route");

            fails(IllegalArgumentException.class,
                    () -> new BackendApiClient(URI.create("http://example.test")),
                    "non-local HTTP endpoints must be rejected");
            fails(IllegalArgumentException.class,
                    () -> client.verifyIdentity(" "),
                    "empty Firebase token must be rejected");
            System.out.println("PASS: backend HTTP client sends token over the expected endpoint");
        } finally {
            server.stop(0);
        }
    }

    private static void reply(
            com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static void fails(
            Class<? extends RuntimeException> expected,
            Runnable action,
            String description) {
        try {
            action.run();
            throw new AssertionError("Expected " + expected.getSimpleName() + ": " + description);
        } catch (RuntimeException exception) {
            if (!expected.isInstance(exception)) {
                throw new AssertionError("Unexpected exception for " + description, exception);
            }
        }
    }
}
