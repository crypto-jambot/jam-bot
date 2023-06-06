package dev.rabbit.exception;

public class GasEstimationFailureException extends Exception {
    private GasEstimationFailureException() {
        super("Failure to estimate gas");
    }

    private GasEstimationFailureException(Throwable cause) {
        super("Failure to estimate gas", cause);
    }
    private GasEstimationFailureException(String message, Throwable cause) {
        super(message, cause);
    }
    private GasEstimationFailureException(String message) {
        super(message);
    }

    public static GasEstimationFailureException of() {
        return new GasEstimationFailureException();
    }

    public static GasEstimationFailureException of(Throwable cause) {
        return new GasEstimationFailureException(cause);
    }

    public static GasEstimationFailureException of(String message) {
        return new GasEstimationFailureException(message);
    }
    public static GasEstimationFailureException of(String message, Throwable throwable) {
        return new GasEstimationFailureException(message, throwable);
    }
}
