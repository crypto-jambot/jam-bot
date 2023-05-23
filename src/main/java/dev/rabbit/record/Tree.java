package dev.rabbit.record;

import com.mongodb.lang.Nullable;
import dev.rabbit.pojo.QuadConsumer;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document
public record Tree(@Field("node") Node node, @Field("swap") Swap swap, @Field("children") List<Tree> children) {
    public void traverse(QuadConsumer<Node, Node, Swap.Exchange, Boolean> duringTraversalFn, Runnable postTraversalFn) {
        children.forEach(child -> child.recursiveTraverse(node, duringTraversalFn, postTraversalFn));
    }

    public void recursiveTraverse(Node parent, QuadConsumer<Node, Node, Swap.Exchange, Boolean> duringTraversalFn,
            Runnable postTraversalFn) {
        this.swap.exchanges().forEach(exchange -> {
            duringTraversalFn.accept(parent, node, exchange, swap.ordered());
            this.children().forEach(child -> {
                child.recursiveTraverse(this.node(), duringTraversalFn, postTraversalFn);
            });
            postTraversalFn.run();
        });
    }

    public record Node(@Field("name") String name, @Field("symbol") String symbol, @Field("address") String address,
                       @Field("decimals") Integer decimals) {
        public static Node of(Node node) {
            return new Node(node.name(), node.symbol(), node.address(), node.decimals());
        }
    }

    public record Swap(@Field("exchanges") List<Exchange> exchanges, @Field("ordered") boolean ordered) {
        public record Exchange(@Field("name") String name, @Field("fee") Integer fee, @Field("pool") String pool,
                               @Field("tickSpacing") @Nullable Integer tickSpacing) {
            public static Exchange of(Exchange exchange) {
                return new Exchange(exchange.name(), exchange.fee(), exchange.pool(), exchange.tickSpacing());
            }
        }
    }
}