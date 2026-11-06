package clyvr.backend.security;

public final class LoginLockedException extends RuntimeException {
    private final long retryAfterSeconds;

    public LoginLockedException(long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
