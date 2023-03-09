package dev.rabbit.exception;

public class InvalidPubSubMessageException extends RuntimeException {

    private InvalidPubSubMessageException(String message) {
        super(String.format("Bad Request: Invalid Pub/Sub message format: %s", message));
    }

    public static InvalidPubSubMessageException of(String message) {
        return new InvalidPubSubMessageException(message);
    }
}
