package clyvr.backend.database;

import java.time.LocalDate;

public record AppointmentChangeSubmission(
        long appointmentId,
        String kind,
        String details,
        LocalDate proposedDate,
        Integer proposedSlot) {
}
