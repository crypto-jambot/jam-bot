package dev.rabbit.exception;

import java.util.HashMap;
import java.util.Map;

public class GasEstimationFailureException extends Exception {
    public static Map<String, Integer> errors = new HashMap<>();
    public static int totalErrors = 0;
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
        errors.put(message, errors.getOrDefault(message, 0) + 1);
        totalErrors++;
        return new GasEstimationFailureException();
    }
}
