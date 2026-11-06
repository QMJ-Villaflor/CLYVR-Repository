package clyvr.backend.database;

import java.time.LocalDate;

public record BackendTimeOffRequest(
        long timeOffId,
        long doctorId,
        LocalDate requestDate,
        int slotNumber,
        String status,
        String reason) {
}
