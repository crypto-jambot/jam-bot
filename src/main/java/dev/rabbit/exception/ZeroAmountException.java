package dev.rabbit.exception;

public class ZeroAmountException extends Exception {
    public ZeroAmountException() {
        super("Zero Reserves or Liquidity calculated");
    }
}
