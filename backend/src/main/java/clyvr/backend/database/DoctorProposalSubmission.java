package clyvr.backend.database;

import java.time.LocalDate;

public record DoctorProposalSubmission(
        long patientId,
        String appointmentType,
        LocalDate appointmentDate,
        int slotNumber,
        String reason) {
}
