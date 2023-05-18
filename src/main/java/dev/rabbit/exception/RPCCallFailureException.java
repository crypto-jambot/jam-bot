package dev.rabbit.exception;

public class RPCCallFailureException extends RuntimeException {
    private RPCCallFailureException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public static RPCCallFailureException of(String message, Throwable throwable) {
        return new RPCCallFailureException(message, throwable);
    }
}
