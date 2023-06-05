package dev.rabbit.exception;

public class GasEstimationFailureException extends Exception {
    private GasEstimationFailureException() {
        super("Failure to estimate gas");
    }

    private GasEstimationFailureException(Throwable cause) {
        super("Failure to estimate gas", cause);
    }

    public static GasEstimationFailureException of() {
        return new GasEstimationFailureException();
    }

    public static GasEstimationFailureException of(Throwable cause) {
        return new GasEstimationFailureException(cause);
    }

    public static GasEstimationFailureException of(String message) {
        return new GasEstimationFailureException();
    }
}
