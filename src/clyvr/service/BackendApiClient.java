package clyvr.service;

import clyvr.database.AvailabilitySlot;
import clyvr.database.AppointmentDecisionResult;
import clyvr.database.BackendAppointment;
import clyvr.database.BackendAppointmentChangeRequest;
import clyvr.database.BackendNotification;
import clyvr.database.BackendTimeOffRequest;
import clyvr.database.AppointmentHold;
import clyvr.database.DoctorAvailability;
import clyvr.database.DoctorDirectoryEntry;
import clyvr.database.BackendPatientProfile;
import clyvr.database.PendingConsultationRequest;
import clyvr.database.PatientProfileUpdate;
import clyvr.database.BackendUserSession;
import clyvr.model.Entities.Role;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class BackendApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final URI verificationEndpoint;
    private final URI baseUri;
    private final Map<Long, String> doctorKeysById = new ConcurrentHashMap<>();
    private final AtomicReference<String> sessionToken = new AtomicReference<>();

    public BackendApiClient(URI backendBaseUri) {
        this(backendBaseUri, HttpClient.newHttpClient());
    }

    BackendApiClient(URI backendBaseUri, HttpClient httpClient) {
        Objects.requireNonNull(backendBaseUri, "backendBaseUri");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        validateBaseUri(backendBaseUri);
        this.baseUri = URI.create(backendBaseUri.toString().replaceAll("/+$", ""));
        this.verificationEndpoint = URI.create(
                baseUri + "/api/v1/auth/verify");
    }

    public CompletableFuture<HttpResponse<String>> verifyIdentity(String firebaseIdToken) {
        if (firebaseIdToken == null || firebaseIdToken.isBlank()) {
            throw new IllegalArgumentException("A Firebase ID token is required.");
        }
        HttpRequest request = HttpRequest.newBuilder(verificationEndpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + firebaseIdToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<BackendUserSession> login(String login, String password) {
        if (login == null || login.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Enter your account name and password.");
        }
        sessionToken.set(null);
        String body = "{\"login\":" + jsonString(login.trim())
                + ",\"password\":" + jsonString(password) + "}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v1/auth/login"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return sendPublicJson(request).thenApply(json -> {
            Map<String, Object> response = asObject(BackendJsonParser.parse(json));
            String token = requiredString(response, "accessToken");
            BackendUserSession account = new BackendUserSession(
                    requiredLong(response, "userId"),
                    Role.valueOf(requiredString(response, "role")),
                    nullableLong(response, "doctorId"),
                    requiredString(response, "firstName"),
                    requiredString(response, "lastName"),
                    requiredString(response, "email"),
                    optionalString(response, "phone"),
                    OffsetDateTime.parse(requiredString(response, "expiresAt")));
            sessionToken.set(token);
            return account;
        });
    }

    public CompletableFuture<Boolean> registerPatient(
            String email, String password, String firstName, String lastName,
            String phone, LocalDate birthDate, String sex, String address) {
        String body = "{"
                + "\"email\":" + jsonString(email) + ","
                + "\"password\":" + jsonString(password) + ","
                + "\"firstName\":" + jsonString(firstName) + ","
                + "\"lastName\":" + jsonString(lastName) + ","
                + "\"phone\":" + jsonString(phone) + ","
                + "\"birthDate\":" + jsonString(birthDate == null ? null : birthDate.toString()) + ","
                + "\"sex\":" + jsonString(sex) + ","
                + "\"address\":" + jsonString(address) + "}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v1/auth/register"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return sendPublicJson(request).thenApply(json ->
                requiredBoolean(asObject(BackendJsonParser.parse(json)), "registered"));
    }

    public CompletableFuture<Boolean> logout() {
        String token = sessionToken.get();
        if (token == null) {
            return CompletableFuture.completedFuture(true);
        }
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v1/auth/logout"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() >= 200
                        && response.statusCode() < 300)
                .whenComplete((ignored, failure) -> sessionToken.compareAndSet(token, null));
    }

    public CompletableFuture<Boolean> changePassword(
            String currentPassword, String newPassword) {
        String body = "{\"currentPassword\":" + jsonString(currentPassword)
                + ",\"newPassword\":" + jsonString(newPassword) + "}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v1/auth/password"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return sendJson(request).thenApply(json ->
                requiredBoolean(asObject(BackendJsonParser.parse(json)), "changed"));
    }

    public CompletableFuture<List<DoctorDirectoryEntry>> findDoctors(String demoIdentity) {
        return sendJson(HttpRequest.newBuilder(baseUri.resolve("/api/v1/doctors"))
                        .GET().build())
                .thenApply(this::parseDoctors);
    }

    public CompletableFuture<DoctorAvailability> findAvailability(
            String demoIdentity, long doctorId, LocalDate day) {
        String doctorKey = doctorKeysById.get(doctorId);
        if (doctorKey == null) {
            throw new IllegalArgumentException(
                    "Refresh the database doctor directory before loading availability.");
        }
        URI uri = baseUri.resolve("/api/v1/patient/availability?doctorKey="
                + doctorKey + "&date=" + day);
        return sendJson(HttpRequest.newBuilder(uri)
                        .GET().build())
                .thenApply(BackendApiClient::parseAvailability);
    }

    public CompletableFuture<DoctorAvailability> findDoctorAvailability(
            String demoIdentity, LocalDate day) {
        Objects.requireNonNull(day, "day");
        URI uri = baseUri.resolve("/api/v1/doctor/availability?date=" + day);
        return sendJson(HttpRequest.newBuilder(uri)
                        .GET().build())
                .thenApply(BackendApiClient::parseAvailability);
    }

    public CompletableFuture<List<PendingConsultationRequest>> findPendingConsultations(
            String demoIdentity) {
        return sendJson(HttpRequest.newBuilder(
                        baseUri.resolve("/api/v1/receptionist/consultations/pending"))
                        .GET().build())
                .thenApply(BackendApiClient::parsePendingConsultations);
    }

    public CompletableFuture<BackendPatientProfile> findPatientProfile(String demoIdentity) {
        return sendJson(HttpRequest.newBuilder(baseUri.resolve("/api/v1/patient/profile"))
                        .GET().build())
                .thenApply(BackendApiClient::parsePatientProfile);
    }

    public CompletableFuture<BackendPatientProfile> updatePatientProfile(
            String demoIdentity, PatientProfileUpdate update) {
        Objects.requireNonNull(update, "update");
        String json = "{"
                + "\"firstName\":" + jsonString(update.firstName()) + ","
                + "\"lastName\":" + jsonString(update.lastName()) + ","
                + "\"phone\":" + jsonString(update.phone()) + ","
                + "\"birthDate\":" + jsonString(update.birthDate()) + ","
                + "\"sex\":" + jsonString(update.sex()) + ","
                + "\"address\":" + jsonString(update.address()) + ","
                + "\"bloodType\":" + jsonString(update.bloodType()) + ","
                + "\"allergies\":" + jsonString(update.allergies()) + ","
                + "\"conditions\":" + jsonString(update.conditions()) + ","
                + "\"medications\":" + jsonString(update.medications()) + ","
                + "\"medicalHistory\":" + jsonString(update.medicalHistory()) + "}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v1/patient/profile"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return sendJson(request).thenApply(BackendApiClient::parsePatientProfile);
    }

    public CompletableFuture<List<BackendAppointment>> findAppointments(String demoIdentity) {
        return sendJson(HttpRequest.newBuilder(baseUri.resolve("/api/v1/appointments"))
                        .GET().build())
                .thenApply(BackendApiClient::parseAppointments);
    }

    public CompletableFuture<BackendAppointment> findAppointment(
            String demoIdentity, long appointmentId) {
        if (appointmentId <= 0) {
            throw new IllegalArgumentException("Appointment ID must be positive.");
        }
        return sendJson(HttpRequest.newBuilder(
                        baseUri.resolve("/api/v1/appointments/" + appointmentId))
                        .GET().build())
                .thenApply(BackendApiClient::parseAppointment);
    }

    public CompletableFuture<AppointmentDecisionResult> decideAppointment(
            String demoIdentity, long appointmentId, boolean approved, String reason) {
        if (appointmentId <= 0 || (!approved && (reason == null || reason.isBlank()))) {
            throw new IllegalArgumentException("Appointment decision is invalid.");
        }
        String json = "{\"approved\":" + approved
                + ",\"reason\":" + jsonString(reason == null ? "" : reason) + "}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                        "/api/v1/receptionist/appointments/" + appointmentId + "/decision"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return sendJson(request).thenApply(BackendApiClient::parseDecisionResult);
    }

    public CompletableFuture<String> cancelPatientAppointment(
            String demoIdentity, long appointmentId) {
        return updateAppointmentStatus(
                demoIdentity, "/api/v1/patient/appointments/", appointmentId, "CANCELLED");
    }

    public CompletableFuture<String> finishDoctorAppointment(
            String demoIdentity, long appointmentId, String status) {
        if (!"COMPLETED".equals(status) && !"NO_SHOW".equals(status)) {
            throw new IllegalArgumentException("The requested doctor status is not supported.");
        }
        return updateAppointmentStatus(
                demoIdentity, "/api/v1/doctor/appointments/", appointmentId, status);
    }

    public CompletableFuture<List<BackendTimeOffRequest>> findDoctorTimeOff(String identity) {
        return sendJson(HttpRequest.newBuilder(baseUri.resolve("/api/v1/doctor/time-off"))
                        .GET().build())
                .thenApply(BackendApiClient::parseTimeOffRequests);
    }

    public CompletableFuture<Long> submitTimeOff(
            String identity, LocalDate day, int slot, String reason) {
        if (day == null || slot < 0 || slot > 7 || reason == null
                || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("Time-off request is invalid.");
        }
        String body = "{\"requestDate\":" + jsonString(day.toString())
                + ",\"slotNumber\":" + slot + ",\"reason\":" + jsonString(reason) + "}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v1/doctor/time-off"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return sendJson(request).thenApply(json ->
                requiredLong(asObject(BackendJsonParser.parse(json)), "timeOffId"));
    }

    public CompletableFuture<String> decideTimeOff(
            String identity, long timeOffId, boolean approved, String reason) {
        if (timeOffId <= 0 || reason == null || reason.length() > 2000) {
            throw new IllegalArgumentException("Time-off decision is invalid.");
        }
        String body = "{\"approved\":" + approved + ",\"reason\":" + jsonString(reason) + "}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                        "/api/v1/receptionist/time-off/" + timeOffId + "/decision"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return sendJson(request).thenApply(json ->
                requiredString(asObject(BackendJsonParser.parse(json)), "status"));
    }

    public CompletableFuture<List<BackendTimeOffRequest>> findPendingTimeOff(String identity) {
        return sendJson(HttpRequest.newBuilder(
                        baseUri.resolve("/api/v1/receptionist/time-off/pending"))
                        .GET().build())
                .thenApply(BackendApiClient::parseTimeOffRequests);
    }

    public CompletableFuture<List<BackendAppointmentChangeRequest>> findPendingAppointmentChanges(
            String identity) {
        return sendJson(HttpRequest.newBuilder(baseUri.resolve(
                        "/api/v1/receptionist/appointment-changes/pending"))
                        .GET().build())
                .thenApply(BackendApiClient::parseAppointmentChanges);
    }

    public CompletableFuture<Long> submitAppointmentChange(
            String identity, long appointmentId, String kind, String details,
            LocalDate proposedDate, Integer proposedSlot) {
        if (appointmentId <= 0 || !Set.of("cancellation", "reschedule").contains(kind)
                || details == null || details.isBlank() || details.length() > 2000
                || ("reschedule".equals(kind)
                    && (proposedDate == null || proposedSlot == null
                        || proposedSlot < 0 || proposedSlot > 7))
                || ("cancellation".equals(kind)
                    && (proposedDate != null || proposedSlot != null))) {
            throw new IllegalArgumentException("Appointment-change request is invalid.");
        }
        String body = "{\"appointmentId\":" + appointmentId
                + ",\"kind\":" + jsonString(kind)
                + ",\"details\":" + jsonString(details)
                + ",\"proposedDate\":" + jsonString(
                        proposedDate == null ? null : proposedDate.toString())
                + ",\"proposedSlot\":" + (proposedSlot == null ? "null" : proposedSlot) + "}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                        "/api/v1/appointments/" + appointmentId + "/changes"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return sendJson(request).thenApply(json ->
                requiredLong(asObject(BackendJsonParser.parse(json)), "requestId"));
    }

    public CompletableFuture<String> decideAppointmentChange(
            String identity, long requestId, boolean approved, String resolution) {
        if (requestId <= 0 || resolution == null || resolution.length() > 2000) {
            throw new IllegalArgumentException("Appointment-change decision is invalid.");
        }
        String body = "{\"approved\":" + approved
                + ",\"resolution\":" + jsonString(resolution) + "}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                        "/api/v1/receptionist/appointment-changes/" + requestId + "/decision"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return sendJson(request).thenApply(json ->
                requiredString(asObject(BackendJsonParser.parse(json)), "status"));
    }

    public CompletableFuture<List<BackendNotification>> findNotifications(String identity) {
        return sendJson(HttpRequest.newBuilder(baseUri.resolve("/api/v1/notifications"))
                        .GET().build())
                .thenApply(BackendApiClient::parseNotifications);
    }

    public CompletableFuture<Boolean> markNotificationRead(String identity, long notificationId) {
        if (notificationId <= 0) {
            throw new IllegalArgumentException("Notification ID must be positive.");
        }
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                        "/api/v1/notifications/" + notificationId + "/read"))
                .PUT(HttpRequest.BodyPublishers.noBody()).build();
        return sendJson(request).thenApply(json -> {
            Map<String, Object> response = asObject(BackendJsonParser.parse(json));
            Object isRead = response.get("isRead");
            if (!(isRead instanceof Boolean read)) {
                throw malformedResponse();
            }
            return read;
        });
    }

    public CompletableFuture<Boolean> notifyAppointmentPatient(
            String identity, long appointmentId, String message) {
        if (appointmentId <= 0 || message == null || message.isBlank() || message.length() > 1000) {
            throw new IllegalArgumentException("Patient notification is invalid.");
        }
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                        "/api/v1/doctor/appointments/" + appointmentId + "/notify-patient"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"message\":" + jsonString(message) + "}"))
                .build();
        return sendJson(request).thenApply(json -> {
            Object created = asObject(BackendJsonParser.parse(json)).get("created");
            if (!(created instanceof Boolean result)) {
                throw malformedResponse();
            }
            return result;
        });
    }

    private CompletableFuture<String> updateAppointmentStatus(
            String demoIdentity, String endpoint, long appointmentId, String status) {
        if (appointmentId <= 0) {
            throw new IllegalArgumentException("Appointment ID must be positive.");
        }
        HttpRequest request = HttpRequest.newBuilder(
                        baseUri.resolve(endpoint + appointmentId + "/status"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(
                        "{\"status\":" + jsonString(status) + "}"))
                .build();
        return sendJson(request).thenApply(json -> requiredString(
                asObject(BackendJsonParser.parse(json)), "status"));
    }

    public CompletableFuture<Long> createConsultation(
            String demoIdentity, String doctorKey, LocalDate day, int slot, String reason) {
        if (doctorKey == null || !doctorKey.matches("D[1-4]")
                || day == null || slot < 0 || slot > 7 || reason == null
                || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("Consultation request is invalid.");
        }
        String body = "{\"doctorKey\":\"" + doctorKey
                + "\",\"appointmentDate\":\"" + day
                + "\",\"slotNumber\":" + slot
                + ",\"reason\":\"" + escapeJson(reason) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v1/patient/consultations"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return sendJson(request).thenApply(json ->
                requiredLong(asObject(BackendJsonParser.parse(json)), "appointmentId"));
    }

    public CompletableFuture<AppointmentHold> createAppointmentHold(
            String demoIdentity, String doctorKey, LocalDate day, int slot) {
        if (doctorKey == null || !doctorKey.matches("D[1-4]")
                || day == null || slot < 0 || slot > 7 || slot % 2 != 0) {
            throw new IllegalArgumentException("Appointment hold request is invalid.");
        }
        String body = "{\"doctorKey\":" + jsonString(doctorKey)
                + ",\"appointmentDate\":" + jsonString(day.toString())
                + ",\"slotNumber\":" + slot + "}";
        HttpRequest request = HttpRequest.newBuilder(
                        baseUri.resolve("/api/v1/patient/appointment-holds"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return sendJson(request).thenApply(json -> {
            Map<String, Object> response = asObject(BackendJsonParser.parse(json));
            return new AppointmentHold(
                    requiredLong(response, "holdId"),
                    OffsetDateTime.parse(requiredString(response, "expiresAt")));
        });
    }

    public CompletableFuture<Long> confirmAppointmentHold(
            String demoIdentity, long holdId, String reason) {
        if (holdId <= 0 || reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("Appointment hold confirmation is invalid.");
        }
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                        "/api/v1/patient/appointment-holds/" + holdId + "/confirm"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"reason\":" + jsonString(reason) + "}"))
                .build();
        return sendJson(request).thenApply(json -> requiredLong(
                asObject(BackendJsonParser.parse(json)), "appointmentId"));
    }

    public CompletableFuture<Boolean> releaseAppointmentHold(String demoIdentity, long holdId) {
        if (holdId <= 0) {
            throw new IllegalArgumentException("Appointment hold ID is invalid.");
        }
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                        "/api/v1/patient/appointment-holds/" + holdId))
                .DELETE()
                .build();
        return sendJson(request).thenApply(json -> requiredBoolean(
                asObject(BackendJsonParser.parse(json)), "released"));
    }

    public CompletableFuture<Long> createDoctorProposal(
            String demoIdentity, long patientId, String appointmentType,
            LocalDate day, int slot, String reason) {
        if (patientId <= 0 || appointmentType == null
                || !Set.of("FOLLOW_UP", "PROCEDURE").contains(appointmentType)
                || day == null || slot < 0 || slot > 7 || reason == null
                || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("Doctor proposal is invalid.");
        }
        String body = "{\"patientId\":" + patientId
                + ",\"appointmentType\":\"" + appointmentType
                + "\",\"appointmentDate\":\"" + day
                + "\",\"slotNumber\":" + slot
                + ",\"reason\":\"" + escapeJson(reason) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v1/doctor/proposals"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return sendJson(request).thenApply(json ->
                requiredLong(asObject(BackendJsonParser.parse(json)), "appointmentId"));
    }

    public CompletableFuture<String> decideDoctorProposal(
            String demoIdentity, long appointmentId, boolean approved,
            LocalDate alternativeDate, Integer alternativeSlot) {
        if (appointmentId <= 0 || (alternativeDate == null) != (alternativeSlot == null)
                || (!approved && alternativeDate != null)
                || (alternativeSlot != null && (alternativeSlot < 0 || alternativeSlot > 7))) {
            throw new IllegalArgumentException("Proposal decision is invalid.");
        }
        String body = "{\"approved\":" + approved;
        if (alternativeDate != null) {
            body += ",\"appointmentDate\":\"" + alternativeDate
                    + "\",\"slotNumber\":" + alternativeSlot;
        }
        body += "}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                        "/api/v1/patient/appointments/" + appointmentId + "/proposal-decision"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return sendJson(request).thenApply(json -> requiredString(
                asObject(BackendJsonParser.parse(json)), "status"));
    }

    private CompletableFuture<String> sendJson(HttpRequest request) {
        String token = sessionToken.get();
        if (token == null) {
            throw new IllegalStateException("Sign in to continue.");
        }
        HttpRequest authorizedRequest = HttpRequest.newBuilder(request, (name, value) -> true)
                .header("Authorization", "Bearer " + token)
                .build();
        return httpClient.sendAsync(authorizedRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        if (response.statusCode() == 401) {
                            sessionToken.compareAndSet(token, null);
                        }
                        throw new BackendApiException(
                                response.statusCode(),
                                response.statusCode() == 401
                                        ? "Your session expired. Please sign in again."
                                        : errorMessage(response.body()));
                    }
                    return response.body();
                });
    }

    private CompletableFuture<String> sendPublicJson(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new BackendApiException(
                                response.statusCode(), errorMessage(response.body()));
                    }
                    return response.body();
                });
    }

    private static String errorMessage(String responseBody) {
        try {
            Object value = asObject(BackendJsonParser.parse(responseBody)).get("message");
            if (value instanceof String message && !message.isBlank()) {
                return message;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to the HTTP status when the server does not return a standard API error.
        }
        return null;
    }

    private List<DoctorDirectoryEntry> parseDoctors(String json) {
        List<Object> rows = asArray(BackendJsonParser.parse(json));
        List<DoctorDirectoryEntry> doctors = new ArrayList<>();
        Map<Long, String> keys = new HashMap<>();
        for (Object value : rows) {
            Map<String, Object> row = asObject(value);
            long id = requiredLong(row, "doctorId");
            String key = requiredString(row, "demoDoctorKey");
            DoctorDirectoryEntry doctor = new DoctorDirectoryEntry(
                    key,
                    id,
                    requiredLong(row, "userId"),
                    requiredString(row, "firstName"),
                    requiredString(row, "lastName"),
                    requiredString(row, "specialty"));
            if (!key.matches("D[1-4]") || keys.put(id, key) != null) {
                throw malformedResponse();
            }
            doctors.add(doctor);
        }
        doctorKeysById.clear();
        doctorKeysById.putAll(keys);
        return List.copyOf(doctors);
    }

    private static List<PendingConsultationRequest> parsePendingConsultations(String json) {
        List<PendingConsultationRequest> requests = new ArrayList<>();
        for (Object value : asArray(BackendJsonParser.parse(json))) {
            Map<String, Object> row = asObject(value);
            requests.add(new PendingConsultationRequest(
                    requiredLong(row, "appointmentId"),
                    requiredLong(row, "patientId"),
                    requiredLong(row, "doctorId"),
                    LocalDate.parse(requiredString(row, "appointmentDate")),
                    Math.toIntExact(requiredNonNegativeLong(row, "slotNumber")),
                    optionalString(row, "reason")));
        }
        return List.copyOf(requests);
    }

    private static List<BackendTimeOffRequest> parseTimeOffRequests(String json) {
        List<BackendTimeOffRequest> requests = new ArrayList<>();
        for (Object value : asArray(BackendJsonParser.parse(json))) {
            Map<String, Object> row = asObject(value);
            long slot = requiredNonNegativeLong(row, "slotNumber");
            if (slot > 7) {
                throw malformedResponse();
            }
            requests.add(new BackendTimeOffRequest(
                    requiredLong(row, "timeOffId"),
                    requiredLong(row, "doctorId"),
                    LocalDate.parse(requiredString(row, "requestDate")),
                    Math.toIntExact(slot),
                    requiredString(row, "status"),
                    optionalString(row, "reason")));
        }
        return List.copyOf(requests);
    }

    private static List<BackendAppointmentChangeRequest> parseAppointmentChanges(String json) {
        List<BackendAppointmentChangeRequest> requests = new ArrayList<>();
        for (Object value : asArray(BackendJsonParser.parse(json))) {
            Map<String, Object> row = asObject(value);
            long slot = requiredNonNegativeLong(row, "appointmentSlot");
            if (slot > 7) {
                throw malformedResponse();
            }
            Object rawProposedSlot = row.get("proposedSlot");
            Integer proposedSlot = null;
            if (rawProposedSlot != null) {
                if (!(rawProposedSlot instanceof Long number) || number < 0 || number > 7) {
                    throw malformedResponse();
                }
                proposedSlot = number.intValue();
            }
            String proposedDateText = optionalString(row, "proposedDate");
            requests.add(new BackendAppointmentChangeRequest(
                    requiredLong(row, "requestId"),
                    requiredLong(row, "appointmentId"),
                    requiredLong(row, "requesterId"),
                    requiredString(row, "kind"),
                    optionalString(row, "details"),
                    requiredString(row, "status"),
                    proposedDateText.isBlank() ? null : LocalDate.parse(proposedDateText),
                    proposedSlot,
                    LocalDate.parse(requiredString(row, "appointmentDate")),
                    Math.toIntExact(slot),
                    requiredString(row, "appointmentType"),
                    requiredString(row, "requesterName"),
                    requiredString(row, "patientName"),
                    requiredString(row, "doctorName")));
        }
        return List.copyOf(requests);
    }

    private static List<BackendNotification> parseNotifications(String json) {
        List<BackendNotification> notifications = new ArrayList<>();
        for (Object value : asArray(BackendJsonParser.parse(json))) {
            Map<String, Object> row = asObject(value);
            Object isRead = row.get("isRead");
            if (!(isRead instanceof Boolean read)) {
                throw malformedResponse();
            }
            notifications.add(new BackendNotification(
                    requiredLong(row, "notificationId"),
                    requiredString(row, "message"),
                    OffsetDateTime.parse(requiredString(row, "createdAt")),
                    read));
        }
        return List.copyOf(notifications);
    }

    private static BackendPatientProfile parsePatientProfile(String json) {
        Map<String, Object> row = asObject(BackendJsonParser.parse(json));
        return new BackendPatientProfile(
                requiredLong(row, "userId"),
                requiredString(row, "firstName"),
                requiredString(row, "lastName"),
                optionalString(row, "email"),
                optionalString(row, "phone"),
                optionalString(row, "birthDate"),
                optionalString(row, "sex"),
                optionalString(row, "address"),
                optionalString(row, "bloodType"),
                optionalString(row, "allergies"),
                optionalString(row, "conditions"),
                optionalString(row, "medications"),
                optionalString(row, "medicalHistory"));
    }

    private static List<BackendAppointment> parseAppointments(String json) {
        List<BackendAppointment> appointments = new ArrayList<>();
        for (Object value : asArray(BackendJsonParser.parse(json))) {
            appointments.add(parseAppointment(value));
        }
        return List.copyOf(appointments);
    }

    private static BackendAppointment parseAppointment(String json) {
        return parseAppointment(BackendJsonParser.parse(json));
    }

    private static BackendAppointment parseAppointment(Object value) {
        Map<String, Object> row = asObject(value);
        long slotNumber = requiredNonNegativeLong(row, "slotNumber");
        if (slotNumber > 7) {
            throw malformedResponse();
        }
        return new BackendAppointment(
                requiredLong(row, "appointmentId"),
                requiredLong(row, "patientId"),
                requiredLong(row, "doctorId"),
                requiredString(row, "appointmentType"),
                LocalDate.parse(requiredString(row, "appointmentDate")),
                Math.toIntExact(slotNumber),
                requiredString(row, "status"),
                optionalString(row, "reason"),
                requiredString(row, "patientFirstName"),
                requiredString(row, "patientLastName"),
                requiredString(row, "doctorFirstName"),
                requiredString(row, "doctorLastName"),
                requiredString(row, "specialty"));
    }

    private static AppointmentDecisionResult parseDecisionResult(String json) {
        Map<String, Object> row = asObject(BackendJsonParser.parse(json));
        Object persisted = row.get("rejectionExplanationPersisted");
        if (!(persisted instanceof Boolean explanationPersisted)) {
            throw malformedResponse();
        }
        return new AppointmentDecisionResult(
                requiredString(row, "status"), explanationPersisted);
    }

    private static DoctorAvailability parseAvailability(String json) {
        Map<String, Object> value = asObject(BackendJsonParser.parse(json));
        List<AvailabilitySlot> slots = new ArrayList<>();
        for (Object item : asArray(value.get("slots"))) {
            Map<String, Object> slot = asObject(item);
            slots.add(new AvailabilitySlot(
                    Math.toIntExact(requiredNonNegativeLong(slot, "slotNumber")),
                    AvailabilitySlot.State.valueOf(requiredString(slot, "state"))));
        }
        return new DoctorAvailability(requiredLong(value, "doctorId"), slots);
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(value) + "\"";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw malformedResponse();
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asArray(Object value) {
        if (!(value instanceof List<?>)) {
            throw malformedResponse();
        }
        return (List<Object>) value;
    }

    private static long requiredLong(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Long number) || number <= 0) {
            throw malformedResponse();
        }
        return number;
    }

    private static long requiredNonNegativeLong(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Long number) || number < 0) {
            throw malformedResponse();
        }
        return number;
    }

    private static Long nullableLong(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Long number) || number <= 0) {
            throw malformedResponse();
        }
        return number;
    }

    private static String requiredString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw malformedResponse();
        }
        return text;
    }

    private static boolean requiredBoolean(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Boolean result)) {
            throw malformedResponse();
        }
        return result;
    }

    private static String optionalString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value instanceof String text ? text : "";
    }

    private static IllegalArgumentException malformedResponse() {
        return new IllegalArgumentException("Backend returned an invalid response.");
    }

    private static void validateBaseUri(URI baseUri) {
        String scheme = baseUri.getScheme();
        String host = baseUri.getHost();
        boolean localHttp = "http".equalsIgnoreCase(scheme)
                && ("localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host));
        if ((! "https".equalsIgnoreCase(scheme) && !localHttp)
                || host == null
                || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null
                || baseUri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Backend URI must use HTTPS (HTTP is allowed only for localhost).");
        }
    }

    public static final class BackendApiException extends RuntimeException {
        private final int statusCode;

        public BackendApiException(int statusCode) {
            this(statusCode, null);
        }

        public BackendApiException(int statusCode, String message) {
            super(message == null
                    ? "The backend could not complete the request (HTTP " + statusCode + ")."
                    : message);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
