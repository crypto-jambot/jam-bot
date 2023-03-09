package dev.rabbit.record;

import java.util.List;
import java.util.function.Consumer;

public record Tree(Node node, Swap swap, List<Tree> children) {
    public record Node(String name, String symbol, String address, int decimals) {}
    public record Swap(List<Exchange> exchanges, boolean ordered) {
        public record Exchange(String name, int fee, String pool) {}
    }

    public void traverse(Consumer<Node> nodeFunction, Consumer<Swap> swapFunction) {
        if (node != null) nodeFunction.accept(this.node);
        if (swap != null) swapFunction.accept(this.swap);
        this.children.forEach(child -> {
            child.traverse(nodeFunction, swapFunction);
        });
    }
}