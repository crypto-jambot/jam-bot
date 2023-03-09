package dev.rabbit.exception;

public class RetrieveSecretFailureException extends RuntimeException {
    public RetrieveSecretFailureException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public RetrieveSecretFailureException(String message) {
        super(message);
    }
}