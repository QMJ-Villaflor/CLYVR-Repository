package clyvr.backend.api;

import clyvr.backend.database.ResolvedDatabaseIdentity;
import clyvr.backend.security.AuthenticatedSession;
import clyvr.backend.security.DatabaseSessionService;
import clyvr.backend.security.PatientRegistration;
import java.sql.SQLException;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public final class SessionController {

    private final DatabaseSessionService sessions;

    public SessionController(DatabaseSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) throws SQLException {
        if (request == null) {
            throw new IllegalArgumentException("Login details are required.");
        }
        AuthenticatedSession session = sessions.login(request.login(), request.password());
        ResolvedDatabaseIdentity identity = session.identity();
        return new LoginResponse(session.accessToken(), session.expiresAt(),
                identity.userId(), identity.role().name(), identity.doctorId(),
                session.firstName(), session.lastName(), session.email(), session.phone());
    }

    @PostMapping("/register")
    public RegistrationResponse register(@RequestBody RegistrationRequest request)
            throws SQLException {
        if (request == null) {
            throw new IllegalArgumentException("Account details are required.");
        }
        sessions.registerPatient(new PatientRegistration(
                request.email(), request.password(), request.firstName(), request.lastName(),
                request.phone(), request.birthDate(), request.sex(), request.address()));
        return new RegistrationResponse(true);
    }

    @PostMapping("/logout")
    public LogoutResponse logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {
        sessions.logout(authorization);
        return new LogoutResponse(true);
    }

    @PostMapping("/password")
    public PasswordChanged changePassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization,
            @RequestBody PasswordChangeRequest request) throws SQLException {
        if (request == null) {
            throw new IllegalArgumentException("Password change details are required.");
        }
        ResolvedDatabaseIdentity identity = sessions.requireIdentity(authorization);
        sessions.changePassword(identity, request.currentPassword(), request.newPassword());
        return new PasswordChanged(true);
    }

    public record LoginRequest(String login, String password) {
    }

    public record RegistrationRequest(
            String email,
            String password,
            String firstName,
            String lastName,
            String phone,
            LocalDate birthDate,
            String sex,
            String address) {
    }

    public record PasswordChangeRequest(String currentPassword, String newPassword) {
    }

    public record LoginResponse(
            String accessToken,
            java.time.OffsetDateTime expiresAt,
            long userId,
            String role,
            Long doctorId,
            String firstName,
            String lastName,
            String email,
            String phone) {
    }

    public record RegistrationResponse(boolean registered) {
    }

    public record LogoutResponse(boolean loggedOut) {
    }

    public record PasswordChanged(boolean changed) {
    }
}
