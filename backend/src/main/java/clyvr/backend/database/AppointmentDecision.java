package clyvr.backend.database;

public record AppointmentDecision(boolean approved, String reason) {
    public AppointmentDecision {
        if (approved) {
            reason = "";
        } else if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("A decision explanation is required.");
        } else {
            reason = reason.trim();
        }
    }
}
