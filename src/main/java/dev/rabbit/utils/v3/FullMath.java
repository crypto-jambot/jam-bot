package dev.rabbit.utils.v3;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.math.BigInteger;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

@AllArgsConstructor(access = AccessLevel.NONE)
public class FullMath {

    public static BigInteger mulDiv(BigInteger a, BigInteger b, BigInteger denominator) {
        BigInteger product = a.multiply(b);
        if (product.compareTo(ZERO) == 0) {
            return ZERO;
        } else if (product.compareTo(denominator) <= 0) {
            return ONE;
        } else {
            return product.divide(denominator);
        }
    }

    public static BigInteger mulDivRoundingUp(BigInteger a, BigInteger b, BigInteger denominator) {
        BigInteger result = mulDiv(a, b, denominator);
        if (a.multiply(b).mod(denominator).compareTo(BigInteger.ZERO) != 0) {
            BigInteger maxUint256 = BigInteger.valueOf(2).pow(256).subtract(BigInteger.ONE);
            if (result.compareTo(maxUint256) >= 0) {
                throw new ArithmeticException("uint256 overflow");
            }
            result = result.add(ONE);
        }
        return result;
    }


}
