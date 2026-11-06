package clyvr.backend.database;

import java.time.LocalDate;
import java.util.Objects;

public record ConsultationSubmission(
        String doctorKey,
        LocalDate appointmentDate,
        int slotNumber,
        String reason) {
    public ConsultationSubmission {
        Objects.requireNonNull(doctorKey);
        Objects.requireNonNull(appointmentDate);
        Objects.requireNonNull(reason);
        if (!doctorKey.matches("D[1-4]")) {
            throw new IllegalArgumentException("Select a configured demo doctor.");
        }
        if (slotNumber < 0 || slotNumber > 7 || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("Consultation details are invalid.");
        }
    }
}
