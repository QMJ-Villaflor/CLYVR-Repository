package clyvr.database;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DoctorScheduleSelection(
        LocalDate day,
        String service,
        DoctorDirectoryEntry selectedDoctor,
        List<DoctorDirectoryEntry> availableDoctors) {

    public DoctorScheduleSelection {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(service, "service");
        availableDoctors = List.copyOf(availableDoctors);
        if (selectedDoctor != null && !availableDoctors.contains(selectedDoctor)) {
            throw new IllegalArgumentException("Selected doctor must be in the database directory.");
        }
    }

    public Long selectedDoctorId() {
        return selectedDoctor == null ? null : selectedDoctor.doctorId();
    }

    public List<DoctorDirectoryEntry> doctorsForService() {
        if (service.equals("All services") || service.startsWith("Unsure")) {
            if (service.startsWith("Unsure")) {
                return availableDoctors.stream()
                    .filter(doctor -> doctor.specialty().equals("General Medicine"))
                    .toList();
            }
            return availableDoctors;
        }
        return availableDoctors.stream()
            .filter(doctor -> doctor.specialty().equals(service))
            .toList();
    }
}
