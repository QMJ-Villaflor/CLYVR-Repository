package clyvr.database;

import java.time.LocalDate;

public record BackendAppointment(
        long appointmentId,
        long patientId,
        long doctorId,
        String appointmentType,
        LocalDate appointmentDate,
        int slotNumber,
        String status,
        String reason,
        String patientFirstName,
        String patientLastName,
        String doctorFirstName,
        String doctorLastName,
        String specialty) {

    public String summary() {
        return "Appointment #" + appointmentId + " · " + appointmentType.replace('_', ' ')
                + " · " + appointmentDate + " · "
                + clyvr.model.ClinicStore.TIMES[slotNumber] + " · " + status.replace('_', ' ');
    }

    public String patientName() {
        return patientFirstName + " " + patientLastName;
    }

    public String doctorName() {
        return "Dr. " + doctorFirstName + " " + doctorLastName;
    }
}
