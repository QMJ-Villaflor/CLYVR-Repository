package clyvr.database;

import java.time.LocalDate;

public record BackendAppointmentChangeRequest(
        long requestId,
        long appointmentId,
        long requesterId,
        String kind,
        String details,
        String status,
        LocalDate proposedDate,
        Integer proposedSlot,
        LocalDate appointmentDate,
        int appointmentSlot,
        String appointmentType,
        String requesterName,
        String patientName,
        String doctorName) {
}
