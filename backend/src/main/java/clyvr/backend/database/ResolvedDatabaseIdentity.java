package clyvr.backend.database;

import clyvr.backend.identity.BackendRole;

public record ResolvedDatabaseIdentity(long userId, BackendRole role, Long doctorId) {
    public ResolvedDatabaseIdentity {
        if (userId <= 0 || role == null || (doctorId != null && doctorId <= 0)) {
            throw new IllegalArgumentException("Database identity is invalid.");
        }
        if ((role == BackendRole.DOCTOR) != (doctorId != null)) {
            throw new IllegalArgumentException("Database doctor identity is inconsistent.");
        }
    }
}
