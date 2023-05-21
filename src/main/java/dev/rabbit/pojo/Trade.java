package dev.rabbit.pojo;

import com.google.cloud.Tuple;
import com.google.common.base.Preconditions;
import dev.rabbit.pool.Pool;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Field;
import java.math.BigInteger;

@Builder
@Getter
public class Trade {
    private final Token start;
    private final Token end;
    private final boolean ordered;
    private final Pool pool;
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
        amountOut = pool.getAmountOutAssumingNoTickCrossings(amountIn, ordered);
    }

    public BigInteger computeAmountOutNoTicks(BigInteger amountIn) {
        return pool.getAmountOutAssumingNoTickCrossings(amountIn, ordered);
    }

    public BigInteger computeAmountInNoTicks(BigInteger amountOut) {
        return pool.getAmountInAssumingNoTickCrossings(amountOut, ordered);
    }

    public BigInteger computeAmountOutWithTicks(BigInteger amountIn) {
        return pool.getAmountOutAssumingNoTickCrossings(amountIn, ordered);
    }

    public BigInteger computeAmountInWithTicks(BigInteger amountOut) {
        return pool.getAmountInAssumingNoTickCrossings(amountOut, ordered);
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