package dev.rabbit.utils.v3;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.math.BigInteger;

import static java.math.BigInteger.ZERO;

@AllArgsConstructor(access = AccessLevel.NONE)
public class LiquidityMath {
    /**
     * Add a signed liquidity delta to liquidity.
     *
     * @param x The liquidity before change
     * @param y The delta by which liquidity should be changed
     * @return The liquidity delta
     */
    public static BigInteger addDelta(BigInteger x, BigInteger y) {
        return y.compareTo(ZERO) < 0 ? x.subtract(y.negate()) : x.add(y);
    }
}
