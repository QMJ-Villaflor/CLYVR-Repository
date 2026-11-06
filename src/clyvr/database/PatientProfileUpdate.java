package clyvr.database;

public record PatientProfileUpdate(
        String firstName,
        String lastName,
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
