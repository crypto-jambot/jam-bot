package dev.rabbit.pojo;

import com.google.cloud.Tuple;
import com.google.common.base.Preconditions;
import dev.rabbit.pool.Pool;
import dev.rabbit.pool.State;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;

@Builder
@Getter
public class Trade {
    private final Token start;
    private final Token end;
    private final boolean ordered;
    private final Pool<? extends State> pool;
    @Setter
    private BigInteger amountIn;
    @Setter
    private BigInteger amountOut;

    public Tuple<BigInteger, BigInteger> getOrderedReserves() {
        BigInteger reserve0 = pool.getReserve0();
        BigInteger reserve1 = pool.getReserve1();
        return this.ordered ? Tuple.of(reserve0, reserve1) : Tuple.of(reserve1, reserve0);
    }

    // Returns back the amountIn or whatever would be the amountIn to swap up the nearest initialized tick
    public BigInteger getFullInputAmountOrToFirstTick() {
        Preconditions.checkState(amountIn != null, "amountIn field is null");
        return pool.getFullInputAmountOrToFirstTick(amountIn, ordered);
    }

    public void computeAndSetAmountOutNoTicks() {
        Preconditions.checkState(amountIn != null, "amountIn field is null");
        amountOut = computeAmountOutNoTicks(amountIn);
    }

    public void computeAndSetAmountOutWithTicks() {
        Preconditions.checkState(amountIn != null, "amountIn field is null");
        amountOut = computeAmountOutWithTicks(amountIn);
    }

    public BigInteger computeAmountOutNoTicks(BigInteger amountIn) {
        return pool.computeAmountOutNoTicks(amountIn, ordered);
    }

    public BigInteger computeAmountInNoTicks(BigInteger amountOut) {
        return pool.computeAmountInNoTicks(amountOut, ordered);
    }

    public BigInteger computeAmountOutWithTicks(BigInteger amountIn) {
        return pool.computeAmountOutWithTicks(amountIn, ordered);
    }

    public BigInteger swap(BigInteger amountIn) {
        return pool.swap(amountIn, ordered, true);
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