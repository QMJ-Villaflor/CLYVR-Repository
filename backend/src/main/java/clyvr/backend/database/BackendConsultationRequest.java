package clyvr.backend.database;

import java.time.LocalDate;

public record BackendConsultationRequest(
        long appointmentId,
        long patientId,
        long doctorId,
        LocalDate appointmentDate,
        int slotNumber,
        String reason) {
    public BackendConsultationRequest {
        if (appointmentId <= 0 || patientId <= 0 || doctorId <= 0
                || appointmentDate == null || slotNumber < 0 || slotNumber > 7) {
            throw new IllegalArgumentException("Consultation request is invalid.");
        }
        reason = reason == null ? "" : reason;
    }
}
