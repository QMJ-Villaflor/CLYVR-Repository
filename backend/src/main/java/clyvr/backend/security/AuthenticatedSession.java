package clyvr.backend.security;

import clyvr.backend.database.ResolvedDatabaseIdentity;
import java.time.OffsetDateTime;

public record AuthenticatedSession(
        String accessToken,
        OffsetDateTime expiresAt,
        ResolvedDatabaseIdentity identity,
        String firstName,
        String lastName,
        String email,
        String phone) {
}
