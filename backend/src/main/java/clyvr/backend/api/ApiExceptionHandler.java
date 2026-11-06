package clyvr.backend.api;

import clyvr.backend.identity.InvalidFirebaseTokenException;
import clyvr.backend.identity.TokenVerifierUnavailableException;
import clyvr.backend.identity.DevelopmentIdentityService;
import clyvr.backend.database.JdbcClyvrDataRepository;
import clyvr.backend.database.AmbiguousPatientProfileException;
import clyvr.backend.database.AppointmentNotFoundException;
import clyvr.backend.database.AppointmentHoldExpiredException;
import clyvr.backend.database.InvalidAppointmentTransitionException;
import clyvr.backend.database.PatientProfileNotFoundException;
import clyvr.backend.security.InvalidCredentialsException;
import clyvr.backend.security.InvalidSessionException;
import clyvr.backend.security.LoginLockedException;
import clyvr.backend.security.PasswordVerificationUnavailableException;
import clyvr.backend.security.ForbiddenRoleException;
import clyvr.backend.security.AccountAlreadyExistsException;
import java.sql.SQLException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ApiExceptionHandler {

    @ExceptionHandler({
        AuthController.UnauthorizedRequestException.class,
        InvalidFirebaseTokenException.class
    })
    public ResponseEntity<ApiError> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("unauthorized", "A valid account session is required."));
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidSessionException.class})
    public ResponseEntity<ApiError> invalidLoginOrSession() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("unauthorized", "The credentials or session are invalid."));
    }

    @ExceptionHandler(LoginLockedException.class)
    public ResponseEntity<ApiError> loginLocked(LoginLockedException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiError("login_locked",
                        "Too many failed sign-in attempts. Try again later.",
                        exception.retryAfterSeconds()));
    }

    @ExceptionHandler(PasswordVerificationUnavailableException.class)
    public ResponseEntity<ApiError> passwordVerificationUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("password_verification_unavailable",
                        "Account password verification is not configured for this hash format."));
    }

    @ExceptionHandler(TokenVerifierUnavailableException.class)
    public ResponseEntity<ApiError> verifierUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("identity_verification_unavailable",
                        "Firebase token verification is not configured or available."));
    }

    @ExceptionHandler(DevelopmentIdentityService.DevelopmentIdentityUnavailableException.class)
    public ResponseEntity<ApiError> developmentIdentityUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("development_identity_disabled",
                        "The local development identity mode is disabled."));
    }

    @ExceptionHandler(DevelopmentIdentityService.InvalidDevelopmentIdentityException.class)
    public ResponseEntity<ApiError> invalidDevelopmentIdentity() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("unauthorized", "The local demo identity could not be verified."));
    }

    @ExceptionHandler({
        DevelopmentIdentityService.ForbiddenRoleException.class,
        ForbiddenRoleException.class
    })
    public ResponseEntity<ApiError> forbiddenRole() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("forbidden", "This database operation is not allowed for this role."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalidInput() {
        return ResponseEntity.badRequest()
                .body(new ApiError("invalid_request", "The request contains invalid values."));
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<ApiError> accountAlreadyExists() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("account_already_exists",
                        "An account with that email address already exists."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformedRequestBody() {
        return ResponseEntity.badRequest()
                .body(new ApiError("invalid_request", "The request body contains invalid values."));
    }

    @ExceptionHandler(JdbcClyvrDataRepository.AppointmentConflictException.class)
    public ResponseEntity<ApiError> appointmentConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("appointment_conflict",
                        "The selected appointment slot is no longer available."));
    }

    @ExceptionHandler(AppointmentHoldExpiredException.class)
    public ResponseEntity<ApiError> appointmentHoldExpired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("appointment_hold_expired",
                        "The temporary reservation expired. Please choose the slot again."));
    }

    @ExceptionHandler({
        PatientProfileNotFoundException.class,
        AppointmentNotFoundException.class
    })
    public ResponseEntity<ApiError> recordNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("record_not_found",
                        "The requested record does not exist or is not accessible."));
    }

    @ExceptionHandler({
        AmbiguousPatientProfileException.class,
        InvalidAppointmentTransitionException.class
    })
    public ResponseEntity<ApiError> recordConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("record_conflict",
                        "The record is ambiguous, its state does not allow this operation, "
                                + "or the schema cannot represent this decision."));
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ApiError> databaseUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("database_unavailable",
                        "The database operation could not be completed."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> configurationUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("configuration_unavailable",
                        "A required local development configuration is unavailable."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internalError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("internal_error", "The request could not be completed."));
    }
}
