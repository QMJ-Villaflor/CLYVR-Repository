package clyvr.backend.database;

import java.time.LocalDate;

public record BackendAppointment(
        long appointmentId,
        long patientId,
        long doctorId,
        String appointmentType,
        LocalDate appointmentDate,
        int slotNumber,
        String status,
        String reason,
        String patientFirstName,
        String patientLastName,
        String doctorFirstName,
        String doctorLastName,
        String specialty) {
}
