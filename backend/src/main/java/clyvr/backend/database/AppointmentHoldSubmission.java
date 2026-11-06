package clyvr.backend.database;

import java.time.LocalDate;

public record AppointmentHoldSubmission(
        String doctorKey, LocalDate appointmentDate, int slotNumber) {
    public AppointmentHoldSubmission {
        if (doctorKey == null || !doctorKey.matches("D[1-4]")
                || appointmentDate == null || slotNumber < 0 || slotNumber > 7) {
            throw new IllegalArgumentException("Appointment hold request is invalid.");
        }
    }
}
