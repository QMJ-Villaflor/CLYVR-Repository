package clyvr.backend.security;

import java.time.LocalDate;

public record PatientRegistration(
        String email,
        String password,
        String firstName,
        String lastName,
        String phone,
        LocalDate birthDate,
        String sex,
        String address) {
}
