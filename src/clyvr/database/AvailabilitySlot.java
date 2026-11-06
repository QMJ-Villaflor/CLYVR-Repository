package clyvr.database;

public record AvailabilitySlot(int slotNumber, State state) {

    public AvailabilitySlot {
        if (slotNumber < 0 || slotNumber > 7) {
            throw new IllegalArgumentException("Slot number must be between 0 and 7.");
        }
        if (state == null) {
            throw new NullPointerException("state");
        }
    }

    public enum State {
        AVAILABLE,
        APPOINTMENT,
        PATIENT_CONFLICT,
        APPROVED_TIME_OFF,
        RESCHEDULE_HOLD,
        ACTIVE_HOLD
    }
}
