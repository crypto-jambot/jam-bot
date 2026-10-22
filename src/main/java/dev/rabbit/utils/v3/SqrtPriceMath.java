/*
 * Java port of the corresponding library in Uniswap v3-core
 * (https://github.com/Uniswap/v3-core), Copyright (C) Uniswap Labs.
 * Original licence: GPL-2.0-or-later (MIT for some libraries).
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package dev.rabbit.utils.v3;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.math.BigInteger;

import static dev.rabbit.utils.Constants.Q96;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

@AllArgsConstructor(access = AccessLevel.NONE)
public class SqrtPriceMath {
    private static BigInteger getNextSqrtPriceFromAmount0RoundingUp(
            BigInteger sqrtPX96,
            BigInteger liquidity,
            BigInteger amount,
            boolean add
    ) {
        // we short circuit amount == 0 because the result is otherwise not guaranteed to equal the input price
        if (amount.equals(ZERO)) {
            return sqrtPX96;
        }
        BigInteger numerator1 = liquidity.shiftLeft(96);

        if (add) {
            BigInteger product = amount.multiply(sqrtPX96);
            if (product.divide(amount).equals(sqrtPX96)) {
                BigInteger denominator = numerator1.add(product);
                if (denominator.compareTo(numerator1) >= 0) {
                    // always fits in 160 bits
                    return FullMath.mulDivRoundingUp(numerator1, sqrtPX96, denominator);
                }
            }

            BigInteger numerator2 = numerator1.divide(sqrtPX96).add(amount);
            return UnsafeMath.divRoundingUp(numerator1, numerator2);
        } else {
            BigInteger product = amount.multiply(sqrtPX96);
            // if the product overflows, we know the denominator underflows
            // in addition, we must check that the denominator does not underflow
            if (product.divide(amount).equals(sqrtPX96) && numerator1.compareTo(product) > 0) {
                BigInteger denominator = numerator1.subtract(product);
                return FullMath.mulDivRoundingUp(numerator1, sqrtPX96, denominator);
            }
            throw new RuntimeException("denominator underflows");
        }
    }

    private static BigInteger getNextSqrtPriceFromAmount1RoundingDown(
            BigInteger sqrtPX96,
            BigInteger liquidity,
            BigInteger amount,
            boolean add
    ) {
        BigInteger maxUint160 = BigInteger.valueOf(2).pow(160).subtract(ONE);
        // if we're adding (subtracting), rounding down requires rounding the quotient down (up)
        // in both cases, avoid a mulDiv for most inputs
        if (add) {
            BigInteger quotient = (
                    amount.compareTo(maxUint160) <= 0
                            ? (amount.shiftLeft(96)).divide(liquidity)
                            : FullMath.mulDiv(amount, Q96, liquidity)
            );

            return sqrtPX96.add(quotient);
        } else {
            BigInteger quotient = (
                    amount.compareTo(maxUint160) <= 0
                            ? UnsafeMath.divRoundingUp(amount.shiftLeft(96), liquidity)
                            : FullMath.mulDivRoundingUp(amount, Q96, liquidity)
            );

            if (sqrtPX96.compareTo(quotient) <= 0) {
                throw new RuntimeException("Invalid input values: sqrtPX96 should be greater than quotient.");
            }
            // always fits 160 bits
            return sqrtPX96.subtract(quotient);
        }
    }

    public static BigInteger getNextSqrtPriceFromOutput(
            BigInteger sqrtPX96,
            BigInteger liquidity,
            BigInteger amountOut,
            boolean zeroForOne
    ) {
        if (sqrtPX96.compareTo(ZERO) <= 0 || liquidity.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("sqrtPX96 and liquidity must be greater than zero.");
        }

        // round to make sure that we pass the target price
        if (zeroForOne) {
            return getNextSqrtPriceFromAmount1RoundingDown(sqrtPX96, liquidity, amountOut, false);
        } else {
            return getNextSqrtPriceFromAmount0RoundingUp(sqrtPX96, liquidity, amountOut, false);
        }
    }

    public static BigInteger getNextSqrtPriceFromInput(
            BigInteger sqrtPX96,
            BigInteger liquidity,
            BigInteger amountIn,
            boolean zeroForOne
    ) {
        if (sqrtPX96.compareTo(ZERO) <= 0 || liquidity.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("sqrtPX96 and liquidity must be greater than zero.");
        }

        // round to make sure that we pass the target price
        if (zeroForOne) {
            return getNextSqrtPriceFromAmount0RoundingUp(sqrtPX96, liquidity, amountIn, true);
        } else {
            return getNextSqrtPriceFromAmount1RoundingDown(sqrtPX96, liquidity, amountIn, true);
        }
    }

    public static BigInteger getAmount0Delta(
            BigInteger sqrtRatioAX96,
            BigInteger sqrtRatioBX96,
            BigInteger liquidity,
            boolean roundUp
    ) {
        if (sqrtRatioAX96.compareTo(sqrtRatioBX96) > 0) {
            BigInteger temp = sqrtRatioAX96;
            sqrtRatioAX96 = sqrtRatioBX96;
            sqrtRatioBX96 = temp;
        }

        BigInteger numerator1 = liquidity.shiftLeft(96);
        BigInteger numerator2 = sqrtRatioBX96.subtract(sqrtRatioAX96);

        if (sqrtRatioAX96.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("sqrtRatioAX96 must be greater than 0");
        }

        return roundUp
                ? UnsafeMath.divRoundingUp(FullMath.mulDivRoundingUp(numerator1, numerator2, sqrtRatioBX96), sqrtRatioAX96)
                : FullMath.mulDiv(numerator1, numerator2, sqrtRatioBX96).divide(sqrtRatioAX96);
    }

    public static BigInteger getAmount1Delta(
            BigInteger sqrtRatioAX96,
            BigInteger sqrtRatioBX96,
            BigInteger liquidity,
            boolean roundUp
    ) {
        if (sqrtRatioAX96.compareTo(sqrtRatioBX96) > 0) {
            BigInteger temp = sqrtRatioAX96;
            sqrtRatioAX96 = sqrtRatioBX96;
            sqrtRatioBX96 = temp;
        }

        return roundUp
                ? FullMath.mulDivRoundingUp(liquidity, sqrtRatioBX96.subtract(sqrtRatioAX96), Q96)
                : FullMath.mulDiv(liquidity, sqrtRatioBX96.subtract(sqrtRatioAX96), Q96);
    }

    public static BigInteger getAmount0Delta(
            BigInteger sqrtRatioAX96,
            BigInteger sqrtRatioBX96,
            BigInteger liquidity
    ) {
        if (liquidity.compareTo(BigInteger.ZERO) < 0) {
            return getAmount0Delta(sqrtRatioAX96, sqrtRatioBX96, liquidity.negate(), false).negate();
        }
        return getAmount0Delta(sqrtRatioAX96, sqrtRatioBX96, liquidity, true);
    }

    public static BigInteger getAmount1Delta(
            BigInteger sqrtRatioAX96,
            BigInteger sqrtRatioBX96,
            BigInteger liquidity
    ) {
        if (liquidity.compareTo(BigInteger.ZERO) < 0) {
            return getAmount1Delta(sqrtRatioAX96, sqrtRatioBX96, liquidity.negate(), false).negate();
        }
        return getAmount1Delta(sqrtRatioAX96, sqrtRatioBX96, liquidity, true);
    }
}
