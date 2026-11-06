package clyvr.backend.database;

import java.time.LocalDate;

public record PatientProposalDecision(
        boolean approved,
        LocalDate appointmentDate,
        Integer slotNumber) {
}
