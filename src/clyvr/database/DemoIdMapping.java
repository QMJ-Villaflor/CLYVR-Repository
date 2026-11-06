package clyvr.database;

import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;

public final class DemoIdMapping {

    private static final Set<String> PATIENT_KEYS = Set.of("U1", "U2");
    private static final Set<String> DOCTOR_KEYS = Set.of("D1", "D2", "D3", "D4");

    private final Map<String, String> environment;

    public DemoIdMapping(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
    }

    public static DemoIdMapping fromSystemEnvironment() {
        return new DemoIdMapping(System.getenv());
    }

    public long patientId(String demoUserId) {
        return resolve(demoUserId, PATIENT_KEYS, "CLYVR_DEMO_USER_ID_");
    }

    public long doctorId(String demoDoctorId) {
        return resolve(demoDoctorId, DOCTOR_KEYS, "CLYVR_DEMO_DOCTOR_ID_");
    }

    public List<Long> configuredDoctorIds() {
        Set<Long> ids = new LinkedHashSet<>();
        for (String demoDoctorId : DOCTOR_KEYS.stream().sorted().toList()) {
            String variable = "CLYVR_DEMO_DOCTOR_ID_" + demoDoctorId;
            String value = environment.get(variable);
            if (value != null && !value.isBlank()) {
                if (!ids.add(resolve(demoDoctorId, DOCTOR_KEYS, "CLYVR_DEMO_DOCTOR_ID_"))) {
                    throw new IllegalStateException(
                        "Multiple demo doctor mappings resolve to the same Supabase doctor ID."
                    );
                }
            }
        }
        return List.copyOf(ids);
    }

    private long resolve(String demoId, Set<String> validKeys, String variablePrefix) {
        if (!validKeys.contains(demoId)) {
            throw new IllegalArgumentException("Unknown demo ID key: " + demoId);
        }

        String variable = variablePrefix + demoId;
        String value = environment.get(variable);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing Supabase ID mapping. Set environment variable " + variable + "."
            );
        }
        if (!value.matches("[0-9]+")) {
            throw new IllegalStateException(
                "Invalid Supabase ID mapping in environment variable " + variable
                    + ": expected a positive bigint."
            );
        }

        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException("ID must be positive");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                "Invalid Supabase ID mapping in environment variable " + variable
                    + ": expected a positive bigint.",
                exception
            );
        }
    }
}
