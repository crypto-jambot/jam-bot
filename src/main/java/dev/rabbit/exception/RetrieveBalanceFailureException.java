package dev.rabbit.exception;

public class RetrieveBalanceFailureException extends RuntimeException {
    public RetrieveBalanceFailureException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
