package clyvr.backend.database;

import java.util.List;

public record BackendAvailability(long doctorId, List<Slot> slots) {
    public BackendAvailability {
        if (doctorId <= 0 || slots.size() != 8) {
            throw new IllegalArgumentException("Availability response is invalid.");
        }
        slots = List.copyOf(slots);
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).slotNumber() != i) {
                throw new IllegalArgumentException("Availability slots are out of order.");
            }
        }
    }

    public record Slot(int slotNumber, String state) {
        public Slot {
            if (slotNumber < 0 || slotNumber > 7 || state == null) {
                throw new IllegalArgumentException("Availability slot is invalid.");
            }
        }
    }
}
