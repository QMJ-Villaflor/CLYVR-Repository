package clyvr.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import clyvr.backend.database.ClyvrDataRepository;
import clyvr.backend.database.ResolvedDatabaseIdentity;
import clyvr.backend.identity.BackendRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseSessionServiceTest {

    private static final String SIGNER_KEY = "c2lnbmVyLWtleS1mb3ItdGVzdHM=";
    private static final String SALT_SEPARATOR = "c2FsdC1mb3ItdGVzdHM=";

    private ClyvrDataRepository repository;
    private FirebaseScrypt hasher;
    private MutableClock clock;
    private DatabaseSessionService sessions;
    private String passwordHash;

    @BeforeEach
    void setUp() {
        repository = mock(ClyvrDataRepository.class);
        hasher = new FirebaseScrypt(SIGNER_KEY, SALT_SEPARATOR);
        clock = new MutableClock(Instant.parse("2026-11-06T12:00:00Z"));
        sessions = new DatabaseSessionService(repository, hasher, clock);
        passwordHash = hasher.hash("SafePass987");
    }

    @Test
    void verifiesPasswordAndIssuesServerSessionMappedToCurrentDatabaseIdentity() throws Exception {
        when(repository.findCredential("test@example.org"))
                .thenReturn(Optional.of(patientCredential(passwordHash)));
        when(repository.findActiveIdentity(402))
                .thenReturn(Optional.of(new ResolvedDatabaseIdentity(
                        402, BackendRole.PATIENT, null)));

        AuthenticatedSession session = sessions.login("test@example.org", "SafePass987");

        assertEquals(402, session.identity().userId());
        assertEquals(BackendRole.PATIENT, sessions.requireIdentity(
                "Bearer " + session.accessToken()).role());
        assertTrue(session.expiresAt().isAfter(java.time.OffsetDateTime.now(clock)));
        assertThrows(InvalidSessionException.class, () -> sessions.requireIdentity(null));
    }

    @Test
    void rejectsBadCredentialsAndLocksAfterFiveFailedAttempts() throws Exception {
        when(repository.findCredential("test@example.org"))
                .thenReturn(Optional.of(patientCredential(passwordHash)));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> sessions.login("test@example.org", "wrong-password"));
        }
        assertThrows(LoginLockedException.class,
                () -> sessions.login("test@example.org", "wrong-password"));
        assertThrows(LoginLockedException.class,
                () -> sessions.login("test@example.org", "SafePass987"));
        verify(repository, never()).findActiveIdentity(anyLong());
    }

    @Test
    void clearsLockoutAfterTheConfiguredWindow() throws Exception {
        when(repository.findCredential("test@example.org"))
                .thenReturn(Optional.of(patientCredential(passwordHash)));
        when(repository.findActiveIdentity(402))
                .thenReturn(Optional.of(new ResolvedDatabaseIdentity(
                        402, BackendRole.PATIENT, null)));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> sessions.login("test@example.org", "wrong-password"));
        }
        assertThrows(LoginLockedException.class,
                () -> sessions.login("test@example.org", "wrong-password"));
        assertThrows(LoginLockedException.class,
                () -> sessions.login("test@example.org", "SafePass987"));

        clock.advance(java.time.Duration.ofMinutes(5));
        assertEquals(402, sessions.login("test@example.org", "SafePass987").identity().userId());
    }

    @Test
    void rechecksDatabaseIdentityForEveryProtectedRequest() throws Exception {
        when(repository.findCredential("test@example.org"))
                .thenReturn(Optional.of(patientCredential(passwordHash)));
        when(repository.findActiveIdentity(402))
                .thenReturn(Optional.of(new ResolvedDatabaseIdentity(
                        402, BackendRole.PATIENT, null)))
                .thenReturn(Optional.of(new ResolvedDatabaseIdentity(
                        402, BackendRole.PATIENT, null)))
                .thenReturn(Optional.empty());

        AuthenticatedSession session = sessions.login("test@example.org", "SafePass987");
        String authorization = "Bearer " + session.accessToken();
        assertEquals(BackendRole.PATIENT, sessions.requireIdentity(authorization).role());
        assertThrows(InvalidSessionException.class,
                () -> sessions.requireIdentity(authorization));
    }

    @Test
    void doesNotModifyAnUnsupportedExistingPasswordHash() throws Exception {
        when(repository.findCredential("test@example.org")).thenReturn(Optional.of(
                new UserCredential(402, "$2a$unsupported", BackendRole.PATIENT,
                        true, null, "Test", "Patient", "test@example.org", "555-0100")));

        assertThrows(PasswordVerificationUnavailableException.class,
                () -> sessions.login("test@example.org", "SafePass987"));
        verify(repository, never()).updatePasswordHash(anyLong(), anyString());
    }

    @Test
    void expiredAndLoggedOutSessionsAreRejected() throws Exception {
        when(repository.findCredential("test@example.org"))
                .thenReturn(Optional.of(patientCredential(passwordHash)));
        when(repository.findActiveIdentity(402))
                .thenReturn(Optional.of(new ResolvedDatabaseIdentity(
                        402, BackendRole.PATIENT, null)));

        AuthenticatedSession session = sessions.login("test@example.org", "SafePass987");
        String authorization = "Bearer " + session.accessToken();
        clock.advance(java.time.Duration.ofHours(8));
        assertThrows(InvalidSessionException.class,
                () -> sessions.requireIdentity(authorization));

        AuthenticatedSession secondSession = sessions.login("test@example.org", "SafePass987");
        String secondAuthorization = "Bearer " + secondSession.accessToken();
        sessions.logout(secondAuthorization);
        assertThrows(InvalidSessionException.class,
                () -> sessions.requireIdentity(secondAuthorization));
    }

    @Test
    void registersPatientUsingServerGeneratedScryptHash() throws Exception {
        when(repository.registerPatient(anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString(), anyString())).thenReturn(903L);

        long userId = sessions.registerPatient(new PatientRegistration(
                "New.Patient@example.org", "SafePass987", "New", "Patient",
                "555-0200", java.time.LocalDate.of(1990, 1, 1),
                "Prefer not to say", "12 Main Street"));

        assertEquals(903, userId);
        var hashCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository).registerPatient(
                org.mockito.ArgumentMatchers.eq("new.patient@example.org"),
                hashCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("New"),
                org.mockito.ArgumentMatchers.eq("Patient"),
                org.mockito.ArgumentMatchers.eq("555-0200"),
                org.mockito.ArgumentMatchers.eq(java.time.LocalDate.of(1990, 1, 1)),
                org.mockito.ArgumentMatchers.eq("Prefer not to say"),
                org.mockito.ArgumentMatchers.eq("12 Main Street"));
        assertTrue(hasher.verify("SafePass987", hashCaptor.getValue()));
    }

    @Test
    void changePasswordUsesCurrentDatabaseIdentityAndRevokesItsSessions() throws Exception {
        when(repository.findCredential("test@example.org"))
                .thenReturn(Optional.of(patientCredential(passwordHash)));
        when(repository.findCredential(402)).thenReturn(Optional.of(patientCredential(passwordHash)));
        when(repository.findActiveIdentity(402))
                .thenReturn(Optional.of(new ResolvedDatabaseIdentity(
                        402, BackendRole.PATIENT, null)));

        AuthenticatedSession session = sessions.login("test@example.org", "SafePass987");
        sessions.changePassword(session.identity(), "SafePass987", "AnotherSafe987");

        var hashCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository).updatePasswordHash(org.mockito.ArgumentMatchers.eq(402L),
                hashCaptor.capture());
        assertTrue(hasher.verify("AnotherSafe987", hashCaptor.getValue()));
        assertThrows(InvalidSessionException.class,
                () -> sessions.requireIdentity("Bearer " + session.accessToken()));
    }

    @Test
    void rejectsInvalidBearerTokenBeforeDatabaseResolution() throws Exception {
        for (String token : new String[] {null, "U2", "Bearer short", "Basic abc"}) {
            assertThrows(InvalidSessionException.class, () -> sessions.requireIdentity(token));
        }
        verify(repository, never()).findActiveIdentity(anyLong());
    }

    private UserCredential patientCredential(String hash) {
        return new UserCredential(402, hash, BackendRole.PATIENT,
                true, null, "Test", "Patient", "test@example.org", "555-0100");
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(java.time.Duration duration) {
            current = current.plus(duration);
        }
    }
}
