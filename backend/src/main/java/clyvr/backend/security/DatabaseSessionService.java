package clyvr.backend.security;

import clyvr.backend.database.ClyvrDataRepository;
import clyvr.backend.database.ResolvedDatabaseIdentity;
import clyvr.backend.identity.BackendRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class DatabaseSessionService {

    private static final Duration SESSION_LIFETIME = Duration.ofHours(8);
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCK = Duration.ofMinutes(5);
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern NAME = Pattern.compile("^[\\p{L}][\\p{L} .'-]*$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClyvrDataRepository repository;
    private final FirebaseScrypt passwordHasher;
    private final Clock clock;
    private final Map<String, LoginAttempts> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public DatabaseSessionService(
            ClyvrDataRepository repository, FirebaseScrypt passwordHasher) {
        this(repository, passwordHasher, Clock.systemUTC());
    }

    DatabaseSessionService(
            ClyvrDataRepository repository, FirebaseScrypt passwordHasher, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.passwordHasher = passwordHasher;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AuthenticatedSession login(String login, String password) throws SQLException {
        if (login == null || login.isBlank() || password == null || password.isEmpty()) {
            throw new InvalidCredentialsException();
        }
        String key = login.trim().toLowerCase(java.util.Locale.ROOT);
        LoginAttempts attempts = loginAttempts.computeIfAbsent(key, ignored -> new LoginAttempts());
        synchronized (attempts) {
            long secondsLocked = attempts.secondsLocked(clock.millis());
            if (secondsLocked > 0) {
                throw new LoginLockedException(secondsLocked);
            }
            UserCredential credential = repository.findCredential(login.trim())
                    .orElseThrow(() -> recordInvalidLogin(attempts));
            requireHasher();
            if (credential.passwordHash() == null
                    || !passwordHasher.supports(credential.passwordHash())) {
                throw new PasswordVerificationUnavailableException();
            }
            if (!credential.active() || !passwordHasher.verify(password, credential.passwordHash())) {
                throw recordInvalidLogin(attempts);
            }
            ResolvedDatabaseIdentity identity = repository.findActiveIdentity(credential.userId())
                    .orElseThrow(InvalidCredentialsException::new);
            attempts.reset();
            loginAttempts.remove(key, attempts);
            return createSession(identity, credential);
        }
    }

    public long registerPatient(PatientRegistration registration) throws SQLException {
        validateRegistration(registration);
        requireHasher();
        String email = registration.email().trim().toLowerCase(java.util.Locale.ROOT);
        String passwordHash = passwordHasher.hash(registration.password());
        return repository.registerPatient(
                email, passwordHash, registration.firstName().trim(),
                registration.lastName().trim(), registration.phone().trim(),
                registration.birthDate(), registration.sex().trim(),
                registration.address().trim());
    }

    public ResolvedDatabaseIdentity requireIdentity(String authorization) throws SQLException {
        String token = bearerToken(authorization);
        String fingerprint = fingerprint(token);
        Session session = sessions.get(fingerprint);
        if (session == null) {
            throw new InvalidSessionException();
        }
        if (!clock.instant().isBefore(session.expiresAt())) {
            sessions.remove(fingerprint, session);
            throw new InvalidSessionException();
        }
        ResolvedDatabaseIdentity identity = repository.findActiveIdentity(session.userId())
                .orElseThrow(() -> {
                    sessions.remove(fingerprint, session);
                    return new InvalidSessionException();
                });
        return identity;
    }

    public void logout(String authorization) {
        String token = bearerToken(authorization);
        sessions.remove(fingerprint(token));
    }

    public void changePassword(
            ResolvedDatabaseIdentity identity, String currentPassword, String newPassword)
            throws SQLException {
        Objects.requireNonNull(identity, "identity");
        UserCredential credential = repository.findCredential(identity.userId())
                .filter(UserCredential::active)
                .orElseThrow(InvalidCredentialsException::new);
        requireHasher();
        if (credential.passwordHash() == null
                || !passwordHasher.supports(credential.passwordHash())) {
            throw new PasswordVerificationUnavailableException();
        }
        if (currentPassword == null
                || !passwordHasher.verify(currentPassword, credential.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        validatePassword(newPassword, credential.email());
        repository.updatePasswordHash(identity.userId(), passwordHasher.hash(newPassword));
        sessions.entrySet().removeIf(entry -> entry.getValue().userId() == identity.userId());
    }

    private AuthenticatedSession createSession(
            ResolvedDatabaseIdentity identity, UserCredential credential) {
        byte[] randomToken = new byte[32];
        RANDOM.nextBytes(randomToken);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomToken);
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(SESSION_LIFETIME);
        sessions.put(fingerprint(token), new Session(identity.userId(), expiresAt.toInstant()));
        return new AuthenticatedSession(token, expiresAt, identity,
                credential.firstName(), credential.lastName(),
                credential.email(), credential.phone());
    }

    private void requireHasher() {
        if (passwordHasher == null) {
            throw new PasswordVerificationUnavailableException();
        }
    }

    private RuntimeException recordInvalidLogin(LoginAttempts attempts) {
        if (attempts.recordFailure(clock.millis())) {
            return new LoginLockedException(LOGIN_LOCK.toSeconds());
        }
        return new InvalidCredentialsException();
    }

    private void validateRegistration(PatientRegistration registration) {
        if (registration == null || registration.email() == null
                || !EMAIL.matcher(registration.email().trim()).matches()
                || registration.email().trim().length() > 254
                || registration.firstName() == null || !validName(registration.firstName())
                || registration.lastName() == null || !validName(registration.lastName())
                || registration.phone() == null || !validPhone(registration.phone())
                || registration.birthDate() == null
                || registration.birthDate().isAfter(java.time.LocalDate.now(clock))
                || registration.birthDate().isBefore(java.time.LocalDate.now(clock).minusYears(120))
                || registration.sex() == null
                || !java.util.Set.of("Female", "Male", "Prefer not to say")
                        .contains(registration.sex().trim())
                || registration.address() == null || registration.address().isBlank()
                || registration.address().trim().length() > 120) {
            throw new IllegalArgumentException("Account details are invalid.");
        }
        validatePassword(registration.password(), registration.email());
    }

    private static void validatePassword(String password, String email) {
        if (password == null || password.length() < 8 || password.length() > 64
                || password.chars().noneMatch(Character::isLetter)
                || password.chars().noneMatch(Character::isDigit)
                || java.util.Set.of("password", "password1", "password123", "12345678",
                        "123456789", "1234567890", "qwerty123", "qwertyuiop",
                        "iloveyou1", "admin123", "letmein123", "welcome123")
                        .contains(password.toLowerCase(java.util.Locale.ROOT))
                || email != null && password.toLowerCase(java.util.Locale.ROOT)
                        .contains(email.trim().toLowerCase(java.util.Locale.ROOT).split("@")[0])
                        && email.trim().split("@")[0].length() >= 4) {
            throw new IllegalArgumentException("Password does not meet the account security rules.");
        }
    }

    private static boolean validName(String value) {
        String trimmed = value.trim();
        return trimmed.length() <= 50 && NAME.matcher(trimmed).matches();
    }

    private static boolean validPhone(String value) {
        String trimmed = value.trim();
        int digits = trimmed.replaceAll("\\D", "").length();
        return trimmed.matches("^\\+?[0-9 ()-]+$") && digits >= 7 && digits <= 15;
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new InvalidSessionException();
        }
        String token = authorization.substring(7).trim();
        if (token.length() < 40 || token.length() > 128
                || !token.matches("[A-Za-z0-9_-]+")) {
            throw new InvalidSessionException();
        }
        return token;
    }

    private static String fingerprint(String token) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record Session(long userId, java.time.Instant expiresAt) {
    }

    private static final class LoginAttempts {
        private int failures;
        private long lockedUntil;

        private long secondsLocked(long now) {
            if (lockedUntil == 0) {
                return 0;
            }
            long remaining = lockedUntil - now;
            if (remaining <= 0) {
                failures = 0;
                lockedUntil = 0;
                return 0;
            }
            return (remaining + 999) / 1000;
        }

        private boolean recordFailure(long now) {
            failures++;
            if (failures >= MAX_LOGIN_ATTEMPTS) {
                lockedUntil = now + LOGIN_LOCK.toMillis();
                return true;
            }
            return false;
        }

        private void reset() {
            failures = 0;
            lockedUntil = 0;
        }
    }
}
