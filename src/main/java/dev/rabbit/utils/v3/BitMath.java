package dev.rabbit.utils.v3;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.math.BigInteger;

@AllArgsConstructor(access = AccessLevel.NONE)
public class BitMath {
    public static int mostSignificantBit(BigInteger x) {
        if (x.compareTo(BigInteger.ZERO) <= 0) {
            throw new IllegalArgumentException("Argument must be positive");
        }

        int r = 0;

        if (x.compareTo(new BigInteger("100000000000000000000000000000000", 16)) >= 0) {
            x = x.shiftRight(128);
            r += 128;
        }
        if (x.compareTo(new BigInteger("10000000000000000", 16)) >= 0) {
            x = x.shiftRight(64);
            r += 64;
        }
        if (x.compareTo(new BigInteger("100000000", 16)) >= 0) {
            x = x.shiftRight(32);
            r += 32;
        }
        if (x.compareTo(new BigInteger("10000", 16)) >= 0) {
            x = x.shiftRight(16);
            r += 16;
        }
        if (x.compareTo(new BigInteger("100", 16)) >= 0) {
            x = x.shiftRight(8);
            r += 8;
        }
        if (x.compareTo(new BigInteger("10", 16)) >= 0) {
            x = x.shiftRight(4);
            r += 4;
        }
        if (x.compareTo(new BigInteger("4", 16)) >= 0) {
            x = x.shiftRight(2);
            r += 2;
        }
        if (x.compareTo(new BigInteger("2", 16)) >= 0) {
            r += 1;
        }

        return r;
    }

    public static int leastSignificantBit(BigInteger x) {
        if (x.compareTo(BigInteger.ZERO) <= 0) {
            throw new IllegalArgumentException("x must be greater than 0");
        }

        int r = 255;

        if (x.and(new BigInteger("340282366920938463463374607431768211455")).compareTo(BigInteger.ZERO) > 0) {
            r -= 128;
        } else {
            x = x.shiftRight(128);
        }
        if (x.and(new BigInteger("18446744073709551615")).compareTo(BigInteger.ZERO) > 0) {
            r -= 64;
        } else {
            x = x.shiftRight(64);
        }
        if (x.and(new BigInteger("4294967295")).compareTo(BigInteger.ZERO) > 0) {
            r -= 32;
        } else {
            x = x.shiftRight(32);
        }
        if (x.and(new BigInteger("65535")).compareTo(BigInteger.ZERO) > 0) {
            r -= 16;
        } else {
            x = x.shiftRight(16);
        }
        if (x.and(new BigInteger("255")).compareTo(BigInteger.ZERO) > 0) {
            r -= 8;
        } else {
            x = x.shiftRight(8);
        }
        if (x.and(new BigInteger("15")).compareTo(BigInteger.ZERO) > 0) {
            r -= 4;
        } else {
            x = x.shiftRight(4);
        }
        if (x.and(new BigInteger("3")).compareTo(BigInteger.ZERO) > 0) {
            r -= 2;
        } else {
            x = x.shiftRight(2);
        }
        if (x.and(new BigInteger("1")).compareTo(BigInteger.ZERO) > 0) {
            r -= 1;
        }

        return r;
    }
}