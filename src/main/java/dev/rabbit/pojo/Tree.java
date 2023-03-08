package dev.rabbit.pojo;

import java.util.List;

public record Tree(Node node, Swap swap, List<Tree> children) {
    public record Node(String name, String symbol, String address, int decimals) {}
    public record Swap(List<Exchange> exchanges, boolean ordered) {
        public record Exchange(String name, int fee, String pool) {}
    }
}
