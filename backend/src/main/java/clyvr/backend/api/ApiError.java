package clyvr.backend.api;

public record ApiError(String error, String message, Long retryAfterSeconds) {
    public ApiError(String error, String message) {
        this(error, message, null);
    }
}
