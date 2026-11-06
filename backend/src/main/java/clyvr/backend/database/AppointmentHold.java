package clyvr.backend.database;

import java.time.OffsetDateTime;

public record AppointmentHold(long holdId, OffsetDateTime expiresAt) {
    public AppointmentHold {
        if (holdId <= 0 || expiresAt == null) {
            throw new IllegalArgumentException("Appointment hold is invalid.");
        }
    }
}
