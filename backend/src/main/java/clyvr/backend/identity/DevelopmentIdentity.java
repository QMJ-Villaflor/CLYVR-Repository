package clyvr.backend.identity;

import java.util.Objects;

public record DevelopmentIdentity(
        String demoKey,
        long userId,
        BackendRole role,
        Long doctorId) {

    public DevelopmentIdentity {
        Objects.requireNonNull(demoKey, "demoKey");
        Objects.requireNonNull(role, "role");
        if (userId <= 0 || (doctorId != null && doctorId <= 0)) {
            throw new IllegalArgumentException("Resolved identity has invalid database identifiers.");
        }
        if ((role == BackendRole.DOCTOR) != (doctorId != null)) {
            throw new IllegalArgumentException("Resolved doctor identity is inconsistent.");
    }
    }
}
