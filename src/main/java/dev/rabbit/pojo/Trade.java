package dev.rabbit.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.rabbit.record.Reserves;
import lombok.Builder;
import lombok.Getter;

import java.math.BigInteger;

@Builder
@Getter
public class Trade {
    private Token start;
    private Token end;
    private boolean ordered;
    private Pool pool;

    public BigInteger[] getOrderedReserves() {
        Reserves reserves = this.pool.getReserves();
        BigInteger reserve0 = reserves.reserve0();
        BigInteger reserve1 = reserves.reserve1();
        return this.ordered ? new BigInteger[]{reserve0, reserve1} : new BigInteger[]{reserve1, reserve0};
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Token start: ").append(start.toString()).append("\n");
        stringBuilder.append("Token end: ").append(end.toString()).append("\n");
        stringBuilder.append("Ordered: ").append(ordered).append("\n");
        stringBuilder.append("Pool: ").append(pool.toString()).append("\n");
        return stringBuilder.toString();
    }
}