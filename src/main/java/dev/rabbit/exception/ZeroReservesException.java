package dev.rabbit.exception;

public class ZeroReservesException extends RuntimeException {
    public ZeroReservesException() {
        super("Zero Reserves or Liquidity calculated");
    }
}
