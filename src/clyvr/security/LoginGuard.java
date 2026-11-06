package clyvr.security;

import java.util.HashMap;
import java.util.Map;

public final class LoginGuard {
    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCK_MILLIS = 5 * 60 * 1000L;

    private static final class State { int failures; long lockedUntil; }
    private static final Map<String, State> STATES = new HashMap<>();

    private LoginGuard() {}

    private static String key(String id) { return id == null ? "" : id.trim().toLowerCase(); }

    public static synchronized long secondsLocked(String id) {
        State s = STATES.get(key(id));
        if (s == null) return 0;
        long left = s.lockedUntil - System.currentTimeMillis();
        if (left <= 0) {
            if (s.lockedUntil != 0) STATES.remove(key(id)); 
            return 0;
        }
        return (left + 999) / 1000;
    }

    public static synchronized int recordFailure(String id) {
        State s = STATES.computeIfAbsent(key(id), k -> new State());
        s.failures++;
        if (s.failures >= MAX_ATTEMPTS) {
            s.lockedUntil = System.currentTimeMillis() + LOCK_MILLIS;
            return 0;
        }
        return MAX_ATTEMPTS - s.failures;
    }

    public static synchronized void recordSuccess(String id) { STATES.remove(key(id)); }

    public static String describe(long seconds) {
        long m = seconds / 60, s = seconds % 60;
        if (m == 0) return s + " second" + (s == 1 ? "" : "s");
        return m + " minute" + (m == 1 ? "" : "s") + (s == 0 ? "" : " " + s + " second" + (s == 1 ? "" : "s"));
    }
}
