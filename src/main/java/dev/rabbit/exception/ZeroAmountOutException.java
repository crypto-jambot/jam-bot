package dev.rabbit.exception;

public class ZeroAmountOutException extends Exception {
    public ZeroAmountOutException() {
        super("Zero Reserves or Liquidity calculated");
    }
}
