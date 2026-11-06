package clyvr.backend.database;

import java.time.LocalDate;

public record TimeOffSubmission(LocalDate requestDate, int slotNumber, String reason) {
}
