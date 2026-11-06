package clyvr.backend.database;

public record AppointmentHoldConfirmation(String reason) {
    public AppointmentHoldConfirmation {
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("Appointment confirmation is invalid.");
        }
    }
}
