package dev.rabbit.exception;

public class PoolClassNotFoundException extends RuntimeException{
    private PoolClassNotFoundException(String poolClassName) {
        super(String.format("Failure to find the pool class for %s", poolClassName));
    }

    public static PoolClassNotFoundException of(String poolClassName) {
        return new PoolClassNotFoundException(poolClassName);
    }
}
