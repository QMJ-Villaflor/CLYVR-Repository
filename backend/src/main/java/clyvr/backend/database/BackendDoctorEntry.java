package clyvr.backend.database;

import java.util.Objects;

public record BackendDoctorEntry(
        String demoDoctorKey,
        long doctorId,
        long userId,
        String firstName,
        String lastName,
        String specialty) {

    public BackendDoctorEntry {
        Objects.requireNonNull(demoDoctorKey);
        Objects.requireNonNull(firstName);
        Objects.requireNonNull(lastName);
        Objects.requireNonNull(specialty);
        if (doctorId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("Doctor identifiers must be positive.");
        }
    }
}
