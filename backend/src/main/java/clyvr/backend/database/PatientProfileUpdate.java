package clyvr.backend.database;

import java.time.LocalDate;

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

    public PatientProfileUpdate {
        firstName = required(firstName, "firstName", 100);
        lastName = required(lastName, "lastName", 100);
        phone = optional(phone, "phone", 40);
        birthDate = optional(birthDate, "birthDate", 10);
        sex = optional(sex, "sex", 40);
        address = optional(address, "address", 500);
        bloodType = optional(bloodType, "bloodType", 40);
        allergies = optional(allergies, "allergies", 2000);
        conditions = optional(conditions, "conditions", 2000);
        medications = optional(medications, "medications", 2000);
        medicalHistory = optional(medicalHistory, "medicalHistory", 4000);
        if (!birthDate.isBlank()) {
            LocalDate parsed;
            try {
                parsed = LocalDate.parse(birthDate);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("birthDate must use YYYY-MM-DD.");
            }
            if (parsed.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("birthDate cannot be in the future.");
            }
        }
    }

    private static String required(String value, String field, int maximumLength) {
        String checked = optional(value, field, maximumLength);
        if (checked.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return checked;
    }

    private static String optional(String value, String field, int maximumLength) {
        if (value == null || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
        return value.trim();
    }
}
