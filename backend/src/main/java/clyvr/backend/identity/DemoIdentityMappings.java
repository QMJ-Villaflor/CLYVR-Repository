package clyvr.backend.identity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DemoIdentityMappings {

    private static final Set<String> USER_KEYS =
            Set.of("U1", "U2", "U3", "U4", "U5", "U6", "U7", "U8");
    private static final Set<String> DOCTOR_KEYS = Set.of("D1", "D2", "D3", "D4");

    private final Map<String, String> values;

    public DemoIdentityMappings(Map<String, String> environment) {
        this.values = Map.copyOf(environment);
    }

    public long userId(String demoUserKey) {
        if (!USER_KEYS.contains(demoUserKey)) {
            throw new IllegalArgumentException("Unknown local demo identity.");
        }
        return resolve("CLYVR_DEMO_USER_ID_" + demoUserKey);
    }

    public long doctorId(String demoDoctorKey) {
        if (!DOCTOR_KEYS.contains(demoDoctorKey)) {
            throw new IllegalArgumentException("Unknown local demo doctor key.");
        }
        return resolve("CLYVR_DEMO_DOCTOR_ID_" + demoDoctorKey);
    }

    public Map<String, Long> configuredDoctorIds() {
        Map<String, Long> configured = new LinkedHashMap<>();
        Set<Long> uniqueIds = new java.util.HashSet<>();
        for (String key : DOCTOR_KEYS.stream().sorted().toList()) {
            String variable = "CLYVR_DEMO_DOCTOR_ID_" + key;
            String value = values.get(variable);
            if (value != null && !value.isBlank()) {
                long id = resolve(variable);
                if (!uniqueIds.add(id)) {
                    throw new IllegalStateException(
                            "Multiple demo doctor keys map to the same database record.");
                }
                configured.put(key, id);
            }
        }
        return Map.copyOf(configured);
    }

    private long resolve(String variable) {
        String value = values.get(variable);
        if (value == null || !value.matches("[0-9]{1,19}")) {
            throw new IllegalStateException(
                    "A required local demo database mapping is missing or invalid.");
        }
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "A required local demo database mapping is missing or invalid.", exception);
        }
    }
}
