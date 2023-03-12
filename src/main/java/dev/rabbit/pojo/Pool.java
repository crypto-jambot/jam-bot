package dev.rabbit.pojo;

import dev.rabbit.record.Exchange;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Pool {
    public static Pool of(Exchange pool) {
        return new Pool(pool.name(), pool.fee(), pool.pool());
    }
    public static Pool of(Pool pool) {
        return new Pool(pool.getExchangeName(), pool.getFee(), pool.getAddress());
    }
    String exchangeName;
    int fee;
    String address;
}
