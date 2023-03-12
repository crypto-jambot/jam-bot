package dev.rabbit.record;

public record Node(String name, String symbol, String address, int decimals) {
    public static Node of(Node node) {
        return new Node(node.name(), node.symbol(), node.address(), node.decimals());
    }
}
