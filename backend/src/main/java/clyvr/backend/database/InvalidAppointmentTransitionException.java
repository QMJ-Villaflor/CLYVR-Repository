package clyvr.backend.database;

public final class InvalidAppointmentTransitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidAppointmentTransitionException() {
        super("The appointment status does not allow this operation.");
    }
}
