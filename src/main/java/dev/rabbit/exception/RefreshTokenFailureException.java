package dev.rabbit.exception;

public class RefreshTokenFailureException extends RuntimeException {
    public RefreshTokenFailureException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
