import clyvr.database.AppointmentRepository;
import clyvr.database.DemoIdMapping;
import clyvr.database.DoctorDirectoryEntry;
import clyvr.database.DoctorRepository;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class DoctorRepositoryTest {

    public static void main(String[] args) throws Exception {
        mapsResultsAndScopesQueryToConfiguredIds();
        reportsMissingLinkedUsers();
        reportsMissingMappedDoctors();
        rejectsAmbiguousDoctorRows();
        returnsEmptyWithoutQueryWhenNoIdsAreConfigured();
        propagatesDatabaseErrors();
        System.out.println("PASS: doctor repository mapping, scope, missing links, ambiguity, and errors");
    }

    private static void mapsResultsAndScopesQueryToConfiguredIds() throws Exception {
        List<Map<String, Object>> rows = List.of(
            row(701L, 1701L, "Ava", "Synthetic", "General Medicine"),
            row(702L, 1702L, "Ben", "Synthetic", "Pediatrics")
        );
        String[] sql = new String[1];
        Map<Integer, Long> parameters = new HashMap<>();
        DoctorRepository repository = repository(
            Map.of(
                "CLYVR_DEMO_DOCTOR_ID_D1", "701",
                "CLYVR_DEMO_DOCTOR_ID_D2", "702"
            ),
            rows, sql, parameters, new AtomicBoolean(), new AtomicBoolean(),
            new AtomicBoolean(), null
        );

        List<DoctorDirectoryEntry> doctors = repository.findMappedDoctors();

        check(doctors.size() == 2, "two mapped synthetic doctors returned");
        check(doctors.get(0).doctorId() == 701L, "numeric database doctor ID mapped");
        check(doctors.get(0).userId() == 1701L, "numeric database user ID mapped");
        check(doctors.get(0).displayName().equals("Dr. Ava Synthetic"), "linked user name mapped");
        check(doctors.get(0).specialty().equals("General Medicine"), "specialty mapped");
        check(sql[0].contains("WHERE d.doctor_id IN (")
            && sql[0].chars().filter(character -> character == '?').count() == 2,
            "query uses one placeholder per configured ID");
        check(parameters.get(1) == 701L && parameters.get(2) == 702L, "only configured numeric IDs bound");
        check(!sql[0].contains("password_hash"), "password hash is not selected");
        check(!sql[0].contains("patient_profiles"), "query does not scan patient profiles");
    }

    private static void reportsMissingLinkedUsers() {
        List<Map<String, Object>> rows = List.of(row(701L, 1701L, null, null, "General Medicine"));
        DoctorRepository repository = repository(
            Map.of("CLYVR_DEMO_DOCTOR_ID_D1", "701"),
            rows, new String[1], new HashMap<>(), new AtomicBoolean(),
            new AtomicBoolean(), new AtomicBoolean(), null
        );

        try {
            repository.findMappedDoctors();
            throw new AssertionError("Expected missing linked user to fail.");
        } catch (SQLException expected) {
            check(expected.getMessage().contains("missing its linked user"), "missing relationship reported");
        }
    }

    private static void rejectsAmbiguousDoctorRows() {
        List<Map<String, Object>> rows = List.of(
            row(701L, 1701L, "Ava", "Synthetic", "General Medicine"),
            row(701L, 1701L, "Ava", "Synthetic", "General Medicine")
        );
        DoctorRepository repository = repository(
            Map.of("CLYVR_DEMO_DOCTOR_ID_D1", "701"),
            rows, new String[1], new HashMap<>(), new AtomicBoolean(),
            new AtomicBoolean(), new AtomicBoolean(), null
        );

        try {
            repository.findMappedDoctors();
            throw new AssertionError("Expected duplicate doctor rows to fail.");
        } catch (SQLException expected) {
            check(expected.getMessage().contains("more than one row"), "ambiguous result reported");
        }
    }

    private static void reportsMissingMappedDoctors() {
        DoctorRepository repository = repository(
            Map.of("CLYVR_DEMO_DOCTOR_ID_D1", "701"),
            List.of(), new String[1], new HashMap<>(), new AtomicBoolean(),
            new AtomicBoolean(), new AtomicBoolean(), null
        );
        try {
            repository.findMappedDoctors();
            throw new AssertionError("Expected missing mapped doctor to fail.");
        } catch (SQLException expected) {
            check(expected.getMessage().contains("IDs were not found"), "missing mapped doctor reported");
        }
    }

    private static void returnsEmptyWithoutQueryWhenNoIdsAreConfigured() throws Exception {
        AtomicBoolean connected = new AtomicBoolean();
        DoctorRepository repository = new DoctorRepository(
            () -> {
                connected.set(true);
                throw new SQLException("Connection must not be attempted.");
            },
            new DemoIdMapping(Map.of())
        );
        check(repository.findMappedDoctors().isEmpty(), "empty mapping gives empty result");
        check(!connected.get(), "no broad query runs without configured IDs");
    }

    private static void propagatesDatabaseErrors() {
        SQLException failure = new SQLException("synthetic read failure", "08006");
        DoctorRepository repository = repository(
            Map.of("CLYVR_DEMO_DOCTOR_ID_D1", "701"),
            List.of(), new String[1], new HashMap<>(), new AtomicBoolean(),
            new AtomicBoolean(), new AtomicBoolean(), failure
        );

        try {
            repository.findMappedDoctors();
            throw new AssertionError("Expected database failure.");
        } catch (SQLException actual) {
            check(actual == failure, "original SQLException propagated");
            check("08006".equals(actual.getSQLState()), "SQLState preserved");
        }
    }

    private static DoctorRepository repository(
            Map<String, String> mapping,
            List<Map<String, Object>> rows,
            String[] sql,
            Map<Integer, Long> parameters,
            AtomicBoolean connectionClosed,
            AtomicBoolean statementClosed,
            AtomicBoolean resultsClosed,
            SQLException executionFailure) {
        return new DoctorRepository(
            () -> connection(rows, sql, parameters, connectionClosed, statementClosed, resultsClosed, executionFailure),
            new DemoIdMapping(mapping)
        );
    }

    private static Connection connection(
            List<Map<String, Object>> rows,
            String[] sql,
            Map<Integer, Long> parameters,
            AtomicBoolean connectionClosed,
            AtomicBoolean statementClosed,
            AtomicBoolean resultsClosed,
            SQLException executionFailure) {
        int[] rowIndex = {-1};
        AtomicBoolean wasNull = new AtomicBoolean();
        ResultSet results = proxy(ResultSet.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "next" -> {
                    rowIndex[0]++;
                    return rowIndex[0] < rows.size();
                }
                case "getLong" -> {
                    Object value = rows.get(rowIndex[0]).get(args[0]);
                    wasNull.set(value == null);
                    return value == null ? 0L : ((Number) value).longValue();
                }
                case "getString" -> {
                    Object value = rows.get(rowIndex[0]).get(args[0]);
                    wasNull.set(value == null);
                    return value;
                }
                case "getBoolean" -> {
                    Object value = rows.get(rowIndex[0]).get(args[0]);
                    wasNull.set(value == null);
                    return value != null && (Boolean) value;
                }
                case "wasNull" -> { return wasNull.get(); }
                case "close" -> { resultsClosed.set(true); return null; }
                default -> { return defaultValue(method.getReturnType()); }
            }
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "setLong" -> { parameters.put((Integer) args[0], (Long) args[1]); return null; }
                case "executeQuery" -> {
                    if (executionFailure != null) throw executionFailure;
                    return results;
                }
                case "close" -> { statementClosed.set(true); return null; }
                default -> { return defaultValue(method.getReturnType()); }
            }
        });
        return proxy(Connection.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "prepareStatement" -> { sql[0] = (String) args[0]; return statement; }
                case "close" -> { connectionClosed.set(true); return null; }
                default -> { return defaultValue(method.getReturnType()); }
            }
        });
    }

    private static Map<String, Object> row(
            long doctorId, long userId, String firstName, String lastName, String specialty) {
        Map<String, Object> row = new HashMap<>();
        row.put("doctor_id", doctorId);
        row.put("user_id", userId);
        row.put("first_name", firstName);
        row.put("last_name", lastName);
        row.put("specialty", specialty);
        row.put("is_active", true);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
