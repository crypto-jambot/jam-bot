package dev.rabbit.exception;

public class GasEstimationFailureException extends Exception {
    private GasEstimationFailureException() {
        super("Failure to estimate gas");
    }

    public static GasEstimationFailureException of() {
        return new GasEstimationFailureException();
    }
}
