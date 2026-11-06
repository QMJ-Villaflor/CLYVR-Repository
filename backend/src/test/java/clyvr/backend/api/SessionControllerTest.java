package clyvr.backend.api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import clyvr.backend.database.ResolvedDatabaseIdentity;
import clyvr.backend.identity.BackendRole;
import clyvr.backend.security.AuthenticatedSession;
import clyvr.backend.security.AccountAlreadyExistsException;
import clyvr.backend.security.DatabaseSessionService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SessionControllerTest {

    private DatabaseSessionService sessions;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sessions = mock(DatabaseSessionService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SessionController(sessions))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void loginReturnsOpaqueSessionAndDatabaseIdentityWithoutCredentialMaterial() throws Exception {
        when(sessions.login("patient@example.org", "SafePass987")).thenReturn(
                new AuthenticatedSession("opaque-session-token-123456789012345678901234",
                        OffsetDateTime.parse("2026-11-06T20:00:00Z"),
                        new ResolvedDatabaseIdentity(402, BackendRole.PATIENT, null),
                        "Test", "Patient", "patient@example.org", "555-0100"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"login\":\"patient@example.org\","
                                + "\"password\":\"SafePass987\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("opaque-session-token-123456789012345678901234"))
                .andExpect(jsonPath("$.userId").value(402))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(content().string(not(containsString("password_hash"))));
    }

    @Test
    void registerForwardsPatientProfileToBackendService() throws Exception {
        when(sessions.registerPatient(any())).thenReturn(903L);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"patient@example.org\","
                                + "\"password\":\"SafePass987\",\"firstName\":\"Test\","
                                + "\"lastName\":\"Patient\",\"phone\":\"555-0100\","
                                + "\"birthDate\":\"1990-01-01\",\"sex\":\"Female\","
                                + "\"address\":\"12 Main Street\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true));
        verify(sessions).registerPatient(any());
    }

    @Test
    void duplicateRegistrationReturnsConflictWithoutDatabaseDetails() throws Exception {
        when(sessions.registerPatient(any())).thenThrow(
                new AccountAlreadyExistsException(new java.sql.SQLException(
                        "duplicate key detail", "23505")));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"patient@example.org\","
                                + "\"password\":\"SafePass987\",\"firstName\":\"Test\","
                                + "\"lastName\":\"Patient\",\"phone\":\"555-0100\","
                                + "\"birthDate\":\"1990-01-01\",\"sex\":\"Female\","
                                + "\"address\":\"12 Main Street\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("duplicate key detail"))));
    }

    @Test
    void logoutRevokesTheProvidedBearerSession() throws Exception {
        doNothing().when(sessions).logout("Bearer opaque-session-token-123456789012345678901234");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization",
                                "Bearer opaque-session-token-123456789012345678901234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedOut").value(true));
        verify(sessions).logout("Bearer opaque-session-token-123456789012345678901234");
    }

    @Test
    void passwordChangeUsesAuthenticatedDatabaseIdentity() throws Exception {
        ResolvedDatabaseIdentity identity =
                new ResolvedDatabaseIdentity(402, BackendRole.PATIENT, null);
        when(sessions.requireIdentity("Bearer opaque-session-token-123456789012345678901234"))
                .thenReturn(identity);
        doNothing().when(sessions).changePassword(eq(identity), eq("OldSafe987"), eq("NewSafe987"));

        mockMvc.perform(post("/api/v1/auth/password")
                        .header("Authorization",
                                "Bearer opaque-session-token-123456789012345678901234")
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"OldSafe987\","
                                + "\"newPassword\":\"NewSafe987\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true));
        verify(sessions).changePassword(identity, "OldSafe987", "NewSafe987");
    }
}
