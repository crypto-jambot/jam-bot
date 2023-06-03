package dev.rabbit.utils.v3;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.math.BigInteger;

import static dev.rabbit.utils.Constants.MAX_UINT128;
import static dev.rabbit.utils.Constants.MAX_UINT16;
import static dev.rabbit.utils.Constants.MAX_UINT32;
import static dev.rabbit.utils.Constants.MAX_UINT64;
import static dev.rabbit.utils.Constants.MAX_UINT8;
import static java.math.BigInteger.ZERO;

@AllArgsConstructor(access = AccessLevel.NONE)
public class BitMath {
    public static int mostSignificantBit(BigInteger x) {
        if (x.compareTo(ZERO) <= 0) {
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
        if (x.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("x must be greater than 0");
        }

        int r = 255;

        if (x.and(MAX_UINT128).compareTo(ZERO) > 0) {
            r -= 128;
        } else {
            x = x.shiftRight(128);
        }
        if (x.and(MAX_UINT64).compareTo(ZERO) > 0) {
            r -= 64;
        } else {
            x = x.shiftRight(64);
        }
        if (x.and(MAX_UINT32).compareTo(ZERO) > 0) {
            r -= 32;
        } else {
            x = x.shiftRight(32);
        }
        if (x.and(MAX_UINT16).compareTo(ZERO) > 0) {
            r -= 16;
        } else {
            x = x.shiftRight(16);
        }
        if (x.and(MAX_UINT8).compareTo(ZERO) > 0) {
            r -= 8;
        } else {
            x = x.shiftRight(8);
        }
        if (x.and(new BigInteger("15")).compareTo(ZERO) > 0) {
            r -= 4;
        } else {
            x = x.shiftRight(4);
        }
        if (x.and(new BigInteger("3")).compareTo(ZERO) > 0) {
            r -= 2;
        } else {
            x = x.shiftRight(2);
        }
        if (x.and(new BigInteger("1")).compareTo(ZERO) > 0) {
            r -= 1;
        }

        return r;
    }
}