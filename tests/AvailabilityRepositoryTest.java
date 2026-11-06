import clyvr.database.AppointmentRepository;
import clyvr.database.AvailabilityRepository;
import clyvr.database.AvailabilitySlot;
import clyvr.database.DemoIdMapping;
import clyvr.database.DoctorAvailability;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class AvailabilityRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);

    public static void main(String[] args) throws Exception {
        mapsScopedAppointmentTimeOffAndRescheduleResults();
        propagatesReadErrors();
        rejectsMissingPatientMappingBeforeConnecting();
        System.out.println("PASS: synthetic availability result mapping, scope, and error handling");
    }

    private static void mapsScopedAppointmentTimeOffAndRescheduleResults() throws Exception {
        Map<String, List<Map<String, Object>>> rows = new HashMap<>();
        rows.put("appointments", List.of(
            row("patient_id", 101L, "doctor_id", 202L, "slot_number", 2),
            row("patient_id", 303L, "doctor_id", 202L, "slot_number", 3),
            row("patient_id", 101L, "doctor_id", 404L, "slot_number", 4)
        ));
        rows.put("time_off_requests", List.of(
            row("slot_number", 5)
        ));
        rows.put("appointment_change_requests", List.of(
            row("patient_id", 303L, "doctor_id", 202L, "proposed_slot", 6, "status", "PENDING"),
            row("patient_id", 101L, "doctor_id", 404L, "proposed_slot", 7, "status", "PENDING"),
            row("patient_id", 303L, "doctor_id", 202L, "proposed_slot", 1, "status", "REJECTED")
        ));
        List<ExecutedQuery> queries = new ArrayList<>();
        AtomicBoolean connectionClosed = new AtomicBoolean();
        AtomicBoolean resultsClosed = new AtomicBoolean();
        AtomicBoolean statementsClosed = new AtomicBoolean();
        AvailabilityRepository repository = repository(
            rows, queries, connectionClosed, resultsClosed, statementsClosed, null
        );

        DoctorAvailability availability = repository.findForDemoPatient("U1", 202L, DAY);

        check(availability.doctorId() == 202L, "numeric database doctor ID retained");
        check(availability.slots().size() == 8, "eight daily slots returned");
        check(state(availability, 0) == AvailabilitySlot.State.AVAILABLE, "unoccupied slot available");
        check(state(availability, 1) == AvailabilitySlot.State.AVAILABLE, "rejected reschedule does not hold slot");
        check(state(availability, 2) == AvailabilitySlot.State.PATIENT_CONFLICT, "patient's own appointment conflicts");
        check(state(availability, 3) == AvailabilitySlot.State.APPOINTMENT, "doctor appointment reserves slot");
        check(state(availability, 4) == AvailabilitySlot.State.PATIENT_CONFLICT, "patient overlap across doctors detected");
        check(state(availability, 5) == AvailabilitySlot.State.APPROVED_TIME_OFF, "approved time off reserves slot");
        check(state(availability, 6) == AvailabilitySlot.State.RESCHEDULE_HOLD, "pending reschedule holds doctor slot");
        check(state(availability, 7) == AvailabilitySlot.State.PATIENT_CONFLICT, "patient reschedule overlap detected");
        check(queries.size() == 3, "appointments, time off, and reschedule queried");

        ExecutedQuery appointmentsQuery = query(queries, "appointments");
        check(appointmentsQuery.sql().contains("appointment_date = ?"), "appointment query scoped by day");
        check(appointmentsQuery.sql().contains("(doctor_id = ? OR patient_id = ?)"), "appointment query scoped by doctor or patient");
        check(appointmentsQuery.values().equals(List.of(DAY, 202L, 101L)), "numeric patient/doctor IDs parameterized");
        check(appointmentsQuery.sql().contains("'PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED'"),
            "only occupancy statuses considered");
        check(!appointmentsQuery.sql().contains("reason"), "query does not load appointment reasons");

        ExecutedQuery timeOffQuery = query(queries, "time_off_requests");
        check(timeOffQuery.values().equals(List.of(202L, DAY)), "time off query scoped to doctor and day");
        ExecutedQuery rescheduleQuery = query(queries, "appointment_change_requests");
        check(rescheduleQuery.values().equals(List.of(DAY, 202L, 101L)),
            "reschedule query scoped to day, doctor, and patient");
        check(connectionClosed.get() && resultsClosed.get() && statementsClosed.get(),
            "connection, statements, and results closed");
    }

    private static void propagatesReadErrors() {
        SQLException failure = new SQLException("synthetic read failure", "08006");
        List<ExecutedQuery> queries = new ArrayList<>();
        AvailabilityRepository repository = repository(
            Map.of(), queries, new AtomicBoolean(), new AtomicBoolean(),
            new AtomicBoolean(), failure
        );
        try {
            repository.findForDemoPatient("U1", 202L, DAY);
            throw new AssertionError("Expected read failure.");
        } catch (SQLException actual) {
            check(actual == failure, "original SQLException propagated");
            check("08006".equals(actual.getSQLState()), "SQLState preserved");
            check(queries.size() == 1, "read stops after failed query");
        }
    }

    private static void rejectsMissingPatientMappingBeforeConnecting() {
        AtomicBoolean connected = new AtomicBoolean();
        AvailabilityRepository repository = new AvailabilityRepository(
            () -> {
                connected.set(true);
                throw new SQLException("Connection must not be attempted.");
            },
            new DemoIdMapping(Map.of())
        );
        try {
            repository.findForDemoPatient("U1", 202L, DAY);
            throw new AssertionError("Expected missing mapping failure.");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("CLYVR_DEMO_USER_ID_U1"), "missing key identified");
            check(!connected.get(), "mapping validation occurs before connecting");
        } catch (SQLException unexpected) {
            throw new AssertionError("Mapping error should happen before JDBC.", unexpected);
        }
    }

    private static AvailabilityRepository repository(
            Map<String, List<Map<String, Object>>> rows,
            List<ExecutedQuery> queries,
            AtomicBoolean connectionClosed,
            AtomicBoolean resultsClosed,
            AtomicBoolean statementsClosed,
            SQLException failure) {
        return new AvailabilityRepository(
            () -> connection(rows, queries, connectionClosed, resultsClosed, statementsClosed, failure),
            new DemoIdMapping(Map.of(
                "CLYVR_DEMO_USER_ID_U1", "101",
                "CLYVR_DEMO_DOCTOR_ID_D1", "202"
            ))
        );
    }

    private static Connection connection(
            Map<String, List<Map<String, Object>>> data,
            List<ExecutedQuery> queries,
            AtomicBoolean connectionClosed,
            AtomicBoolean resultsClosed,
            AtomicBoolean statementsClosed,
            SQLException failure) {
        return proxy(Connection.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "prepareStatement" -> {
                    String sql = (String) args[0];
                    String table = sql.contains("appointment_change_requests") ? "appointment_change_requests"
                        : sql.contains("time_off_requests") ? "time_off_requests" : "appointments";
                    Map<Integer, Object> parameters = new HashMap<>();
                    ResultSet resultSet = resultSet(
                        data.getOrDefault(table, List.of()), resultsClosed
                    );
                    PreparedStatement statement = proxy(PreparedStatement.class, (sProxy, sMethod, sArgs) -> {
                        switch (sMethod.getName()) {
                            case "setObject", "setLong" -> {
                                parameters.put((Integer) sArgs[0], sArgs[1]);
                                return null;
                            }
                            case "executeQuery" -> {
                                queries.add(new ExecutedQuery(sql, ordered(parameters)));
                                if (failure != null) throw failure;
                                return resultSet;
                            }
                            case "close" -> { statementsClosed.set(true); return null; }
                            default -> { return defaultValue(sMethod.getReturnType()); }
                        }
                    });
                    return statement;
                }
                case "close" -> { connectionClosed.set(true); return null; }
                default -> { return defaultValue(method.getReturnType()); }
            }
        });
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows, AtomicBoolean closed) {
        int[] index = {-1};
        AtomicBoolean wasNull = new AtomicBoolean();
        return proxy(ResultSet.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "next" -> { index[0]++; return index[0] < rows.size(); }
                case "getLong" -> {
                    Object value = rows.get(index[0]).get(args[0]);
                    wasNull.set(value == null);
                    return value == null ? 0L : ((Number) value).longValue();
                }
                case "getInt" -> {
                    Object value = rows.get(index[0]).get(args[0]);
                    wasNull.set(value == null);
                    return value == null ? 0 : ((Number) value).intValue();
                }
                case "getString" -> {
                    Object value = rows.get(index[0]).get(args[0]);
                    wasNull.set(value == null);
                    return value;
                }
                case "wasNull" -> { return wasNull.get(); }
                case "close" -> { closed.set(true); return null; }
                default -> { return defaultValue(method.getReturnType()); }
            }
        });
    }

    private static List<Object> ordered(Map<Integer, Object> parameters) {
        List<Object> values = new ArrayList<>();
        for (int i = 1; i <= parameters.size(); i++) values.add(parameters.get(i));
        return List.copyOf(values);
    }

    private static ExecutedQuery query(List<ExecutedQuery> queries, String table) {
        return queries.stream().filter(query -> query.sql().contains(table)).findFirst()
            .orElseThrow(() -> new AssertionError("Query not found for " + table));
    }

    private static AvailabilitySlot.State state(DoctorAvailability availability, int slot) {
        return availability.slots().get(slot).state();
    }

    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) row.put((String) pairs[i], pairs[i + 1]);
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

    private record ExecutedQuery(String sql, List<Object> values) {}
}
