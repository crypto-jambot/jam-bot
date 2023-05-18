package dev.rabbit.utils.v3;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.math.BigInteger;

import static dev.rabbit.utils.Constants.MAX_SQRT_RATIO;
import static dev.rabbit.utils.Constants.MAX_TICK;
import static dev.rabbit.utils.Constants.MAX_UINT256;
import static dev.rabbit.utils.Constants.MIN_SQRT_RATIO;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

@AllArgsConstructor(access = AccessLevel.NONE)
public class TickMath {
    public static BigInteger getSqrtRatioAtTick(BigInteger tick) {
        BigInteger absTick = tick.compareTo(ZERO) < 0 ? tick.negate() : tick;
        if (absTick.compareTo(MAX_TICK) > 0) {
            throw new IllegalArgumentException("Tick exceeds the max tick");
        }

        BigInteger ratio = absTick.and(ONE).equals(BigInteger.ONE) ? new BigInteger("fffcb933bd6fad37aa2d162d1a594001", 16) : new BigInteger("100000000000000000000000000000000", 16);
        if (!(absTick.and(new BigInteger("2", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("fff97272373d413259a46990580e213a", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("4", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("fff2e50f5f656932ef12357cf3c7fdcc", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("8", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("ffe5caca7e10e4e61c3624eaa0941cd0", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("10", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("ffcb9843d60f6159c9db58835c926644", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("20", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("ff973b41fa98c081472e6896dfb254c0", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("40", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("ff2ea16466c96a3843ec78b326b52861", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("80", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("fe5dee046a99a2a811c461f1969c3053", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("100", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("fcbe86c7900a88aedcffc83b479aa3a4", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("200", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("f987a7253ac413176f2b074cf7815e54", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("400", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("f3392b0822b70005940c7a398e4b70f3", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("800", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("e7159475a2c29b7443b29c7fa6e889d9", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("1000", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("d097f3bdfd2022b8845ad8f792aa5825", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("2000", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("a9f746462d870fdf8a65dc1f90e061e5", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("4000", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("70d869a156d2a1b890bb3df62baf32f7", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("8000", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("31be135f97d08fd981231505542fcfa6", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("10000", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("9aa508b5b7a84e1c677de54f3e99bc9", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("20000", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("5d6af8dedb81196699c329225ee604", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("40000", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("2216e584f5fa1ea926041bedfe98", 16)).shiftRight(128);
        if (!(absTick.and(new BigInteger("80000", 16)).equals(ZERO))) ratio = ratio.multiply(new BigInteger("48a170391f7dc42444e8fa2", 16)).shiftRight(128);

        if (tick.compareTo(ZERO) > 0) ratio = MAX_UINT256.divide(ratio);

        // this divides by 1<<32 rounding up to go from a Q128.128 to a Q128.96.
        // we then downcast because we know the result always fits within 160 bits due to our tick input constraint
        // we round up in the division so getTickAtSqrtRatio of the output price is always consistent
        return ratio.shiftRight(32).add(ratio.mod(ONE.shiftLeft(32)).equals(ZERO) ? ZERO : ONE);
    }

    public static BigInteger getTickAtSqrtRatio(BigInteger sqrtPriceX96) {
        if (sqrtPriceX96.compareTo(MIN_SQRT_RATIO) < 0 || sqrtPriceX96.compareTo(MAX_SQRT_RATIO) >= 0) {
            throw new RuntimeException("Invalid sqrtPriceX96 value");
        }

        BigInteger ratio = sqrtPriceX96.shiftLeft(32);

        BigInteger r = ratio;
        int msb = 0;

        for (int i = 7; i >= 0; i--) {
            BigInteger threshold = BigInteger.valueOf(2).pow(64 * (i + 1));
            int f = (r.compareTo(threshold) > 0) ? 1 : 0;
            msb |= f;
            r = r.shiftRight(f);
            msb <<= 1;
        }

        if (msb >= 128) {
            r = ratio.shiftRight(msb - 127);
        } else {
            r = ratio.shiftLeft(127 - msb);
        }

        BigInteger log2 = BigInteger.valueOf(msb - 128).shiftLeft(64);

        for (int i = 0; i < 12; i++) {
            r = r.multiply(r).shiftRight(127);
            BigInteger f = r.shiftRight(128);
            log2 = log2.or(f.shiftLeft(63 - i));
            r = r.shiftRight(f.intValue());
        }

        BigInteger logSqrt10001 = log2.multiply(new BigInteger("255738958999603826347141")); // 128.128 number

        BigInteger tickLow = logSqrt10001.subtract(new BigInteger("3402992956809132418596140100660247210")).shiftRight(128);
        BigInteger tickHi = logSqrt10001.add(new BigInteger("291339464771989622907027621153398088495")).shiftRight(128);

        return (tickLow.equals(tickHi))
                ? tickLow
                : (getSqrtRatioAtTick(tickHi).compareTo(sqrtPriceX96) <= 0) ? tickHi : tickLow;
    }
}
