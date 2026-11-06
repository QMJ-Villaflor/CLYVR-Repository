import clyvr.database.AppointmentRepository;
import clyvr.database.DemoIdMapping;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class AppointmentRepositoryTest {

    private static final long GENERATED_ID = 987654L;
    private static final LocalDate DATE = LocalDate.of(2026, 12, 1);

    public static void main(String[] args) throws Exception {
        verifiesInsertMappingAndGeneratedId();
        propagatesDatabaseErrors();
        rejectsMissingMappingBeforeConnecting();
        System.out.println("PASS: appointment repository mapping, parameters, generated ID, and errors");
    }

    private static void verifiesInsertMappingAndGeneratedId() throws Exception {
        Map<Integer, Object> parameters = new HashMap<>();
        AtomicBoolean connectionClosed = new AtomicBoolean();
        AtomicBoolean statementClosed = new AtomicBoolean();
        AtomicBoolean resultClosed = new AtomicBoolean();
        String[] sql = new String[1];
        AppointmentRepository repository = new AppointmentRepository(
            () -> connection(sql, parameters, connectionClosed, statementClosed, resultClosed, null),
            mapping()
        );

        long id = repository.insertConsultationRequest("U1", "D1", DATE, 2, "Synthetic test reason");

        check(id == GENERATED_ID, "database-generated ID returned");
        check(sql[0].contains("INSERT INTO public.appointments"), "appointment insert target");
        check(sql[0].contains("RETURNING appointment_id"), "generated appointment ID requested");
        check(sql[0].contains("'CONSULTATION'"), "appointment type fixed to consultation");
        check(sql[0].contains("'PENDING_RECEPTIONIST'"), "initial status fixed");
        check(parameters.get(1).equals(101L), "mapped patient bigint");
        check(parameters.get(2).equals(201L), "mapped doctor bigint");
        check(parameters.get(3).equals(DATE), "appointment date");
        check(parameters.get(4).equals(2), "slot number");
        check(parameters.get(5).equals("Synthetic test reason"), "reason parameter");
        check(connectionClosed.get() && statementClosed.get() && resultClosed.get(), "JDBC resources closed");
    }

    private static void propagatesDatabaseErrors() {
        SQLException expected = new SQLException("synthetic database failure", "23505");
        AppointmentRepository repository = new AppointmentRepository(
            () -> connection(new String[1], new HashMap<>(), new AtomicBoolean(),
                new AtomicBoolean(), new AtomicBoolean(), expected),
            mapping()
        );
        try {
            repository.insertConsultationRequest("U1", "D1", DATE, 2, "Synthetic test reason");
            throw new AssertionError("Expected repository to propagate SQLException.");
        } catch (SQLException actual) {
            check(actual == expected, "original SQL exception preserved");
            check("23505".equals(actual.getSQLState()), "SQLState preserved");
        }
    }

    private static void rejectsMissingMappingBeforeConnecting() {
        AtomicBoolean connected = new AtomicBoolean();
        AppointmentRepository repository = new AppointmentRepository(
            () -> {
                connected.set(true);
                throw new SQLException("Connection must not be attempted.");
            },
            new DemoIdMapping(Map.of("CLYVR_DEMO_DOCTOR_ID_D1", "201"))
        );
        try {
            repository.insertConsultationRequest("U1", "D1", DATE, 2, "Synthetic test reason");
            throw new AssertionError("Expected missing mapping failure.");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("CLYVR_DEMO_USER_ID_U1"), "missing key named");
            check(!connected.get(), "mapping validated before database connection");
        } catch (SQLException unexpected) {
            throw new AssertionError("Mapping error should happen before JDBC.", unexpected);
        }
    }

    private static Connection connection(
            String[] sql,
            Map<Integer, Object> parameters,
            AtomicBoolean connectionClosed,
            AtomicBoolean statementClosed,
            AtomicBoolean resultClosed,
            SQLException executionFailure) {
        ResultSet resultSet = proxy(ResultSet.class, (proxy, method, args) -> switch (method.getName()) {
            case "next" -> true;
            case "getLong" -> GENERATED_ID;
            case "wasNull" -> false;
            case "close" -> { resultClosed.set(true); yield null; }
            default -> defaultValue(method.getReturnType());
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "setLong", "setInt", "setObject", "setString" -> {
                    parameters.put((Integer) args[0], args[1]);
                    return null;
                }
                case "executeQuery" -> {
                    if (executionFailure != null) {
                        throw executionFailure;
                    }
                    return resultSet;
                }
                case "close" -> { statementClosed.set(true); return null; }
                default -> { return defaultValue(method.getReturnType()); }
            }
        });
        return proxy(Connection.class, (proxy, method, args) -> switch (method.getName()) {
            case "prepareStatement" -> { sql[0] = (String) args[0]; yield statement; }
            case "close" -> { connectionClosed.set(true); yield null; }
            default -> defaultValue(method.getReturnType());
        });
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

    private static DemoIdMapping mapping() {
        return new DemoIdMapping(Map.of(
            "CLYVR_DEMO_USER_ID_U1", "101",
            "CLYVR_DEMO_DOCTOR_ID_D1", "201"
        ));
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
