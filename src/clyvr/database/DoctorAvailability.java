package clyvr.database;

import java.util.List;
import java.util.Objects;

public record DoctorAvailability(long doctorId, List<AvailabilitySlot> slots) {

    public DoctorAvailability {
        if (doctorId <= 0) {
            throw new IllegalArgumentException("Database doctor ID must be positive.");
        }
        slots = List.copyOf(slots);
        if (slots.size() != 8) {
            throw new IllegalArgumentException("A doctor availability result must contain eight slots.");
        }
        for (int i = 0; i < slots.size(); i++) {
            Objects.requireNonNull(slots.get(i), "slot");
            if (slots.get(i).slotNumber() != i) {
                throw new IllegalArgumentException("Availability slots must be ordered from 0 to 7.");
            }
        }
    }
}
