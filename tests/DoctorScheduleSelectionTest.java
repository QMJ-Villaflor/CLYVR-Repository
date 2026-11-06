import clyvr.database.DoctorDirectoryEntry;
import clyvr.database.DoctorScheduleSelection;
import java.time.LocalDate;
import java.util.List;

public class DoctorScheduleSelectionTest {

    public static void main(String[] args) {
        DoctorDirectoryEntry selected = new DoctorDirectoryEntry(
            987654321L, 123456789L, "Synthetic", "Doctor", "General Medicine"
        );
        DoctorDirectoryEntry other = new DoctorDirectoryEntry(
            987654322L, 123456790L, "Another", "Doctor", "Pediatrics"
        );
        DoctorScheduleSelection selection = new DoctorScheduleSelection(
            LocalDate.of(2026, 12, 1),
            "All services",
            selected,
            List.of(selected, other)
        );

        check(selection.selectedDoctorId() == 987654321L,
            "selected database doctor ID passes through numerically");
        check(selection.doctorsForService().equals(List.of(selected, other)),
            "database directory options are preserved");
        check(!selection.selectedDoctorId().toString().startsWith("D"),
            "database ID is not converted into a demo doctor key");

        DoctorScheduleSelection serviceSelection = new DoctorScheduleSelection(
            LocalDate.of(2026, 12, 1),
            "Pediatrics",
            null,
            List.of(selected, other)
        );
        check(serviceSelection.selectedDoctorId() == null,
            "service-only selection does not invent a doctor ID");
        check(serviceSelection.doctorsForService().equals(List.of(other)),
            "service filtering uses database directory specialty");
        System.out.println("PASS: numeric database doctor selection is preserved through schedule routing");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
