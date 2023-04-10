package dev.rabbit.exception;

public class EthPriceRequestFailureException extends Exception {
    private EthPriceRequestFailureException(String tokenSymbol) {
        super(String.format("Failure to get the eth price for %s", tokenSymbol));
    }

    public static EthPriceRequestFailureException of(String tokenSymbol) {
        return new EthPriceRequestFailureException(tokenSymbol);
    }
}
