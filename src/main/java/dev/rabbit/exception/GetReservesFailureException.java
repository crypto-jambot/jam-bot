package dev.rabbit.exception;

public class GetReservesFailureException extends RuntimeException {
    private GetReservesFailureException(Throwable throwable) {
        super("Failure to get reserves", throwable);
    }

    public static GetReservesFailureException of(Throwable throwable) {
        return new GetReservesFailureException(throwable);
    }
}
