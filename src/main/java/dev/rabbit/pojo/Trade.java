package dev.rabbit.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.rabbit.record.Reserves;
import lombok.Builder;
import lombok.Getter;

import java.math.BigInteger;

@Builder
@Getter
public class Trade {
    @JsonProperty
    private Token start;
    @JsonProperty
    private Token end;
    private boolean ordered;
    @JsonProperty
    private Pool pool;

    public BigInteger[] getOrderedReserves() {
        Reserves reserves = this.pool.getReserves();
        BigInteger reserve0 = reserves.reserve0();
        BigInteger reserve1 = reserves.reserve1();
        return this.ordered ? new BigInteger[]{reserve0, reserve1} : new BigInteger[]{reserve1, reserve0};
    }
}