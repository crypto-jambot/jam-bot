package dev.rabbit.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.rabbit.record.Exchange;
import dev.rabbit.record.Reserves;
import lombok.Getter;

import java.math.BigInteger;
import java.util.Map;

@Getter
public class Pool {
    private final String exchangeName;
    private final int fee;
    private final String address;
    @JsonProperty
    private Reserves reserves;
    private Pool(String exchangeName, int fee, String address) {
        this.exchangeName = exchangeName;
        this.fee = fee;
        this.address = address;
    }
    public static Pool of(Exchange pool) {
        return new Pool(pool.name(), pool.fee(), pool.pool());
    }
    public static Pool of(Pool pool) {
        return new Pool(pool.getExchangeName(), pool.getFee(), pool.getAddress());
    }

    public void addReserves(Map<String, Reserves> reservesMap) {
        this.reserves = reservesMap.get(this.address);
    }
}
