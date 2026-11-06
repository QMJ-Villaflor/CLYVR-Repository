package clyvr.backend.database;

public record BackendPatientProfile(
        long userId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String birthDate,
        String sex,
        String address,
        String bloodType,
        String allergies,
        String conditions,
        String medications,
        String medicalHistory) {
}
