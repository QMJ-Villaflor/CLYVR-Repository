package clyvr.database;

import clyvr.model.Entities.Role;
import java.time.OffsetDateTime;
import java.util.Objects;

public record BackendUserSession(
        long userId,
        Role role,
        Long doctorId,
        String firstName,
        String lastName,
        String email,
        String phone,
        OffsetDateTime expiresAt) {

    public BackendUserSession {
        if (userId <= 0 || role == null || expiresAt == null
                || firstName == null || lastName == null || email == null || phone == null
                || (role == Role.DOCTOR) != (doctorId != null)) {
            throw new IllegalArgumentException("Authenticated account response is invalid.");
        }
        Objects.requireNonNull(role, "role");
    }

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }
}
