package dev.rabbit.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.record.Exchange;
import dev.rabbit.record.Node;
import lombok.Getter;

import java.util.LinkedList;

public class Path {
    private static final ObjectMapper mapper = new ObjectMapper();
    @JsonProperty
    private final LinkedList<Trade> trades = new LinkedList<>();
    public static Path of() {
        return new Path();
    }
    public static Path of(Path path) {
        Path newPath = new Path();
        path.trades.forEach(newPath::addTrade);
        return newPath;
    }
    public void addTrade(Node start, Node end, Exchange pool, boolean ordered) {
        Trade trade = new Trade();
        trade.start = Token.of(start);
        trade.end = Token.of(end);
        trade.pool = Pool.of(pool);
        trade.ordered = ordered;
        trades.add(trade);
    }

    public void addTrade(Trade trade) {
        Trade newTrade = new Trade();
        newTrade.start = Token.of(trade.getStart());
        newTrade.end = Token.of(trade.getEnd());
        newTrade.pool = Pool.of(trade.getPool());
        newTrade.ordered = trade.isOrdered();
        trades.add(newTrade);
    }

    public Trade removeLast() {
        return this.trades.removeLast();
    }

    @Override
    public String toString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Getter
    class Trade {
        @JsonProperty
        private Token start;
        @JsonProperty
        private Token end;
        private boolean ordered;
        @JsonProperty
        private Pool pool;
    }
}
