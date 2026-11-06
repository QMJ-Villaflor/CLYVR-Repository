package clyvr.backend.security;

public final class AccountAlreadyExistsException extends RuntimeException {
    public AccountAlreadyExistsException(Throwable cause) {
        super(cause);
    }
}
