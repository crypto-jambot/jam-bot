package dev.rabbit.record;

import dev.rabbit.pojo.QuadConsumer;

import java.util.List;

public record Tree(Node node, Swap swap, List<Tree> children) {
    public void traverse(QuadConsumer<Node, Node, Swap.Exchange, Boolean> duringTraversalFn, Runnable postTraversalFn) {
        children.forEach(child -> child.recursiveTraverse(node, duringTraversalFn, postTraversalFn));
    }
    public void recursiveTraverse(Node parent, QuadConsumer<Node, Node, Swap.Exchange, Boolean> duringTraversalFn, Runnable postTraversalFn) {
        this.swap.exchanges().forEach(exchange -> {
            duringTraversalFn.accept(parent, node, exchange, swap.ordered());
            this.children().forEach(child -> {
                child.recursiveTraverse(this.node(), duringTraversalFn, postTraversalFn);
            });
            postTraversalFn.run();
        });
    }
    public record Node(String name, String symbol, String address, int decimals) {
        public static Node of(Node node) {
            return new Node(node.name(), node.symbol(), node.address(), node.decimals());
        }
    }
    public record Swap(List<Exchange> exchanges, boolean ordered) {
        public record Exchange(String name, int fee, String pool, int tickSpacing) {
            public static Exchange of(Exchange exchange) {
                return new Exchange(exchange.name(), exchange.fee(), exchange.pool(), exchange.tickSpacing());
            }
        }
    }
}