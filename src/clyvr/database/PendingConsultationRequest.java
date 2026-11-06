package clyvr.database;

import java.time.LocalDate;
import java.util.Objects;

public record PendingConsultationRequest(
        long appointmentId,
        long patientId,
        long doctorId,
        LocalDate appointmentDate,
        int slotNumber,
        String reason) {

    public PendingConsultationRequest {
        if (appointmentId <= 0 || patientId <= 0 || doctorId <= 0) {
            throw new IllegalArgumentException("Database IDs must be positive.");
        }
        Objects.requireNonNull(appointmentDate, "appointmentDate");
        if (slotNumber < 0 || slotNumber > 7) {
            throw new IllegalArgumentException("Slot number must be between 0 and 7.");
        }
        reason = reason == null ? "" : reason;
    }
}
