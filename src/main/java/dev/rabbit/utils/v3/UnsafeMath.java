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

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

@AllArgsConstructor(access = AccessLevel.NONE)
public class UnsafeMath {
    public static BigInteger divRoundingUp(BigInteger x, BigInteger y) {
        BigInteger quotient = x.divide(y);
        if (x.mod(y).compareTo(ZERO) > 0) {
            quotient = quotient.add(ONE);
        }
        return quotient;
    }
}
