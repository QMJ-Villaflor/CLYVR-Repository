import clyvr.database.AppointmentRepository;
import clyvr.database.DemoIdMapping;
import clyvr.database.PendingConsultationRequest;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class PendingConsultationRepositoryTest {

    public static void main(String[] args) throws Exception {
        mapsPendingRowsWithoutTreatingDatabaseIdsAsDemoIds();
        propagatesReadErrorsAndClosesResources();
        rejectsInvalidRows();
        System.out.println("PASS: pending consultation query mapping and errors");
    }

    private static void mapsPendingRowsWithoutTreatingDatabaseIdsAsDemoIds() throws Exception {
        List<Map<String, Object>> rows = List.of(
            row(90001L, 81001L, 71001L, LocalDate.of(2026, 12, 1), 2, "Synthetic consultation"),
            row(90002L, 81002L, 71002L, LocalDate.of(2026, 12, 2), 4, "Follow-up symptoms")
        );
        String[] sql = new String[1];
        AtomicBoolean connectionClosed = new AtomicBoolean();
        AtomicBoolean statementClosed = new AtomicBoolean();
        AtomicBoolean resultsClosed = new AtomicBoolean();
        AppointmentRepository repository = repository(
            rows, sql, connectionClosed, statementClosed, resultsClosed, null
        );

        List<PendingConsultationRequest> requests = repository.findPendingConsultationRequests();

        check(requests.size() == 2, "both pending consultation rows returned");
        check(requests.get(0).appointmentId() == 90001L, "database appointment ID mapped");
        check(requests.get(0).patientId() == 81001L, "database patient ID mapped as bigint");
        check(requests.get(0).doctorId() == 71001L, "database doctor ID mapped as bigint");
        check(requests.get(0).appointmentDate().equals(LocalDate.of(2026, 12, 1)), "date mapped");
        check(requests.get(0).slotNumber() == 2, "slot mapped");
        check(requests.get(0).reason().equals("Synthetic consultation"), "reason mapped");
        check(sql[0].contains("appointment_type = 'CONSULTATION'"), "query filters consultation type");
        check(sql[0].contains("status = 'PENDING_RECEPTIONIST'"), "query filters pending status");
        check(sql[0].contains("ORDER BY appointment_date, slot_number, appointment_id"), "stable queue order");
        check(sql[0].stripLeading().startsWith("SELECT") && !sql[0].contains("UPDATE")
            && !sql[0].contains("DELETE") && !sql[0].contains("INSERT"), "repository query is read-only");
        check(connectionClosed.get() && statementClosed.get() && resultsClosed.get(), "JDBC resources closed");
        try {
            requests.add(requests.get(0));
            throw new AssertionError("Expected an immutable result list.");
        } catch (UnsupportedOperationException expected) {
            check(true, "result list is immutable");
        }
    }

    private static void propagatesReadErrorsAndClosesResources() {
        SQLException expected = new SQLException("synthetic read failure", "08006");
        AtomicBoolean connectionClosed = new AtomicBoolean();
        AtomicBoolean statementClosed = new AtomicBoolean();
        AppointmentRepository repository = repository(
            List.of(), new String[1], connectionClosed, statementClosed,
            new AtomicBoolean(), expected
        );

        try {
            repository.findPendingConsultationRequests();
            throw new AssertionError("Expected the database read error.");
        } catch (SQLException actual) {
            check(actual == expected, "original SQLException propagated");
            check("08006".equals(actual.getSQLState()), "SQLState preserved");
            check(connectionClosed.get() && statementClosed.get(), "resources closed on read error");
        }
    }

    private static void rejectsInvalidRows() {
        List<Map<String, Object>> rows = List.of(
            row(90001L, 81001L, 71001L, LocalDate.of(2026, 12, 1), 8, "Invalid slot")
        );
        AppointmentRepository repository = repository(
            rows, new String[1], new AtomicBoolean(), new AtomicBoolean(),
            new AtomicBoolean(), null
        );
        try {
            repository.findPendingConsultationRequests();
            throw new AssertionError("Expected an invalid database row to fail.");
        } catch (SQLException expected) {
            check(expected.getMessage().contains("invalid pending consultation"), "invalid row reported clearly");
        }
    }

    private static AppointmentRepository repository(
            List<Map<String, Object>> rows,
            String[] sql,
            AtomicBoolean connectionClosed,
            AtomicBoolean statementClosed,
            AtomicBoolean resultsClosed,
            SQLException executionFailure) {
        return new AppointmentRepository(
            () -> connection(rows, sql, connectionClosed, statementClosed, resultsClosed, executionFailure),
            new DemoIdMapping(Map.of())
        );
    }

    private static Connection connection(
            List<Map<String, Object>> rows,
            String[] sql,
            AtomicBoolean connectionClosed,
            AtomicBoolean statementClosed,
            AtomicBoolean resultsClosed,
            SQLException executionFailure) {
        AtomicBoolean resultWasNull = new AtomicBoolean();
        int[] rowIndex = {-1};
        ResultSet results = proxy(ResultSet.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "next" -> {
                    rowIndex[0]++;
                    return rowIndex[0] < rows.size();
                }
                case "getLong", "getInt", "getString", "getObject" -> {
                    Object value = rows.get(rowIndex[0]).get(args[0]);
                    resultWasNull.set(value == null);
                    if (method.getName().equals("getObject")) return value;
                    if (method.getName().equals("getLong")) return value == null ? 0L : ((Number) value).longValue();
                    if (method.getName().equals("getInt")) return value == null ? 0 : ((Number) value).intValue();
                    return value;
                }
                case "wasNull" -> { return resultWasNull.get(); }
                case "close" -> { resultsClosed.set(true); return null; }
                default -> { return defaultValue(method.getReturnType()); }
            }
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (proxy, method, args) -> {
            switch (method.getName()) {
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
            long appointmentId, long patientId, long doctorId, LocalDate date, int slot, String reason) {
        Map<String, Object> row = new HashMap<>();
        row.put("appointment_id", appointmentId);
        row.put("patient_id", patientId);
        row.put("doctor_id", doctorId);
        row.put("appointment_date", date);
        row.put("slot_number", slot);
        row.put("reason", reason);
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
