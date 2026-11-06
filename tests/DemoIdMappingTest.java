import clyvr.database.DemoIdMapping;
import java.util.Map;

public class DemoIdMappingTest {

    public static void main(String[] args) {
        DemoIdMapping mapping = new DemoIdMapping(Map.of(
            "CLYVR_DEMO_USER_ID_U1", "101",
            "CLYVR_DEMO_DOCTOR_ID_D1", "201"
        ));
        check(mapping.patientId("U1") == 101L, "patient fixture mapping");
        check(mapping.doctorId("D1") == 201L, "doctor fixture mapping");

        fails(IllegalStateException.class, () -> mapping.patientId("U2"), "missing mapping");
        fails(
            IllegalStateException.class,
            () -> new DemoIdMapping(Map.of("CLYVR_DEMO_USER_ID_U1", "abc")).patientId("U1"),
            "non-numeric mapping"
        );
        fails(
            IllegalStateException.class,
            () -> new DemoIdMapping(Map.of("CLYVR_DEMO_USER_ID_U1", "0")).patientId("U1"),
            "non-positive mapping"
        );
        fails(
            IllegalStateException.class,
            () -> new DemoIdMapping(Map.of("CLYVR_DEMO_USER_ID_U1", "9223372036854775808")).patientId("U1"),
            "out-of-range bigint mapping"
        );
        fails(IllegalArgumentException.class, () -> mapping.patientId("U3"), "non-patient demo key");
        fails(IllegalArgumentException.class, () -> mapping.doctorId("U1"), "non-doctor demo key");

        System.out.println("PASS: explicit demo ID mappings and validation");
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static void fails(Class<? extends RuntimeException> expected, Runnable action, String description) {
        try {
            action.run();
            throw new AssertionError("Expected " + expected.getSimpleName() + ": " + description);
        } catch (RuntimeException exception) {
            if (!expected.isInstance(exception)) {
                throw new AssertionError("Unexpected exception for " + description, exception);
            }
        }
    }
}
