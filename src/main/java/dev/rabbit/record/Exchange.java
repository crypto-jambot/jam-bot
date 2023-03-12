package dev.rabbit.record;

public record Exchange(String name, int fee, String pool) {
    public static Exchange of(Exchange exchange) {
        return new Exchange(exchange.name(), exchange.fee(), exchange.pool());
    }
}
