package dev.rabbit.record;

import dev.rabbit.pojo.QuadConsumer;

import java.util.List;
import java.util.function.Consumer;

public record Tree(Node node, Swap swap, List<Tree> children) {
    public void traverse(Consumer<Swap> swapFunction) {
        if (swap != null) swapFunction.accept(this.swap);
        this.children.forEach(child -> child.traverse(swapFunction));
    }
    public void traverse(QuadConsumer<Node, Node, Exchange, Boolean> duringTraversalFn, Runnable postTraversalFn) {
        this.children().forEach(child -> child.recursiveTraverse(this.node(), duringTraversalFn, postTraversalFn));
    }
    public void recursiveTraverse(Node parent, QuadConsumer<Node, Node, Exchange, Boolean> duringTraversalFn, Runnable postTraversalFn) {
        this.swap.exchanges().forEach(exchange -> {
            duringTraversalFn.accept(parent, this.node(), exchange, this.swap().ordered());
            this.children().forEach(child -> {
                child.recursiveTraverse(this.node(), duringTraversalFn, postTraversalFn);
            });
            postTraversalFn.run();
        });
    }
}