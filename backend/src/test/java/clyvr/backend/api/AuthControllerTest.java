package clyvr.backend.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import clyvr.backend.identity.FirebaseTokenVerifier;
import clyvr.backend.identity.InvalidFirebaseTokenException;
import clyvr.backend.identity.TokenVerifierUnavailableException;
import clyvr.backend.identity.VerifiedIdentity;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private AtomicReference<FirebaseTokenVerifier> verifier;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        verifier = new AtomicReference<>(token -> new VerifiedIdentity("firebase-user-123"));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(token -> verifier.get().verify(token)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void returnsOnlyVerifiedUidForValidToken() throws Exception {
        AtomicReference<String> suppliedToken = new AtomicReference<>();
        verifier.set(token -> {
            suppliedToken.set(token);
            return new VerifiedIdentity("firebase-user-123");
        });

        mockMvc.perform(post("/api/v1/auth/verify")
                        .header("Authorization", "Bearer header.payload.signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("firebase-user-123"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.role").doesNotExist());

        org.junit.jupiter.api.Assertions.assertEquals(
                "header.payload.signature", suppliedToken.get());
    }

    @Test
    void rejectsInvalidTokenWithoutReturningVerifierDetails() throws Exception {
        String internalDetail = "credential-material-must-not-leak";
        verifier.set(token -> {
            throw new InvalidFirebaseTokenException();
        });

        mockMvc.perform(post("/api/v1/auth/verify")
                        .header("Authorization", "Bearer header.payload.signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString(internalDetail))));
    }

    @Test
    void rejectsMissingAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void rejectsMalformedAuthorizationHeaderBeforeVerification() throws Exception {
        AtomicReference<Boolean> verifierCalled = new AtomicReference<>(false);
        verifier.set(token -> {
            verifierCalled.set(true);
            return new VerifiedIdentity("firebase-user-123");
        });

        mockMvc.perform(post("/api/v1/auth/verify")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        org.junit.jupiter.api.Assertions.assertFalse(verifierCalled.get());
    }

    @Test
    void reportsUnconfiguredVerifierAsServiceUnavailable() throws Exception {
        verifier.set(token -> {
            throw new TokenVerifierUnavailableException();
        });

        mockMvc.perform(post("/api/v1/auth/verify")
                        .header("Authorization", "Bearer header.payload.signature"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("identity_verification_unavailable"));
    }

    @Test
    void doesNotReturnUnexpectedExceptionDetails() throws Exception {
        String secretLikeValue = "private-database-config-marker";
        verifier.set(token -> {
            throw new UnsupportedOperationException(secretLikeValue);
        });

        mockMvc.perform(post("/api/v1/auth/verify")
                        .header("Authorization", "Bearer header.payload.signature"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString(secretLikeValue))));
    }
}
