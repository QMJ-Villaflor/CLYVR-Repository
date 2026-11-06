package clyvr.backend.api;

import clyvr.backend.identity.FirebaseTokenVerifier;
import clyvr.backend.identity.VerifiedIdentity;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public final class AuthController {

    private static final int MAX_TOKEN_LENGTH = 8192;
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(?i)^Bearer ([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)$");

    private final FirebaseTokenVerifier tokenVerifier;

    public AuthController(FirebaseTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @PostMapping("/verify")
    public AuthVerificationResponse verify(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {
        String token = extractBearerToken(authorization);
        VerifiedIdentity identity = tokenVerifier.verify(token);
        return new AuthVerificationResponse(identity.firebaseUid(), true);
    }

    private static String extractBearerToken(String authorization) {
        if (authorization == null || authorization.length() > MAX_TOKEN_LENGTH + 7) {
            throw new UnauthorizedRequestException();
        }
        Matcher matcher = BEARER_TOKEN.matcher(authorization);
        if (!matcher.matches() || matcher.group(1).length() > MAX_TOKEN_LENGTH) {
            throw new UnauthorizedRequestException();
        }
        return matcher.group(1);
    }

    public record AuthVerificationResponse(String uid, boolean verified) {
    }

    public static final class UnauthorizedRequestException extends RuntimeException {
    }

}
