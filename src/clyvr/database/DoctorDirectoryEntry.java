package clyvr.database;

import java.util.Objects;

public record DoctorDirectoryEntry(
        String demoDoctorKey,
        long doctorId,
        long userId,
        String firstName,
        String lastName,
        String specialty) {

    public DoctorDirectoryEntry {
        demoDoctorKey = demoDoctorKey == null ? "" : demoDoctorKey.trim();
        if (doctorId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("Database IDs must be positive.");
        }
        firstName = Objects.requireNonNull(firstName, "firstName").trim();
        lastName = Objects.requireNonNull(lastName, "lastName").trim();
        specialty = Objects.requireNonNull(specialty, "specialty").trim();
        if (firstName.isEmpty() || lastName.isEmpty() || specialty.isEmpty()) {
            throw new IllegalArgumentException("Doctor directory fields must not be blank.");
        }
    }

    public DoctorDirectoryEntry(
            long doctorId, long userId, String firstName, String lastName, String specialty) {
        this("", doctorId, userId, firstName, lastName, specialty);
    }

    public String displayName() {
        return "Dr. " + firstName + " " + lastName;
    }
}
