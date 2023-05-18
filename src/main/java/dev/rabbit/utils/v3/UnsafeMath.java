package dev.rabbit.utils.v3;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.math.BigInteger;

@AllArgsConstructor(access = AccessLevel.NONE)
public class UnsafeMath {
    public static BigInteger divRoundingUp(BigInteger x, BigInteger y) {
        BigInteger quotient = x.divide(y);
        if (x.mod(y).compareTo(BigInteger.ZERO) > 0) {
            quotient = quotient.add(BigInteger.ONE);
        }
        return quotient;
    }
}
