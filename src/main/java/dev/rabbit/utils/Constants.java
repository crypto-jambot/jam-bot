package dev.rabbit.utils;

import dev.rabbit.contracts.JamBot;
import dev.rabbit.generated.ERC20;
import dev.rabbit.generated.UniswapV2Pair;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.TWO;

@AllArgsConstructor(access = AccessLevel.NONE)
public class Constants {
    public static Function getJamExecuteTradeFn(String startToken, String target, byte[] payload) {
        return new Function(
                JamBot.FUNC_EXECUTETRADE,
                Arrays.<Type>asList(new Address(160, startToken),
                        new Address(160, target),
                        new org.web3j.abi.datatypes.DynamicBytes(payload)),
                Collections.<TypeReference<?>>emptyList());
    }
    public static Function getV2SwapFn(BigInteger amount0Out, BigInteger amount1Out, String to, byte[] data) {
        return new Function(UniswapV2Pair.FUNC_SWAP, Arrays.asList(new Uint256(amount0Out), new Uint256(amount1Out), new Address(160, to), new DynamicBytes(data)), Collections.emptyList());
    }

    public static Function getERC20TransferFn(String to, BigInteger value) {
        return new Function(ERC20.FUNC_TRANSFER, Arrays.asList(new Address(160, to), new Uint256(value)), Collections.emptyList());
    }

    public static BigDecimal feeToPercent(int fee) {
        return ONE_MILLION_BD.subtract(BigDecimal.valueOf(fee)).divide(ONE_MILLION_BD, 4, RoundingMode.UNNECESSARY);
    }

    public static final String NULL_ADDRESS = "0x0000000000000000000000000000000000000000";
    public static final String DEPLOYER_ADDRESS = "0x3BfFe20e72b0c90752EbBEcBd1805e8b25ef35F5";
    public static final String JAM_BOT_ADDRESS = "0x685FF453680af0C5AA5327111e4BC5E49B240128";
    public static final String WETH_ADDRESS = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2";
    public static final String DAI_ADDRESS = "0x6B175474E89094C44Da98b954EedeAC495271d0F";
    public static final String USDC_ADDRESS = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";
    public static final BigDecimal R2 = BigDecimal.ONE;
    public static final BigDecimal ONE_MILLION_BD = BigDecimal.valueOf(1000000);
    public static final BigInteger ONE_MILLION_BI = BigInteger.valueOf(1000000);
    public static final BigInteger ONE_GWEI = BigInteger.valueOf(1000000000);
    public static final BigInteger Q96 = TWO.pow(96);
    public static final BigInteger MIN_TICK = BigInteger.valueOf(-887272);
    public static final BigInteger MAX_TICK = BigInteger.valueOf(887272);
    public static final BigInteger MIN_SQRT_RATIO = new BigInteger("4295128739");
    public static final BigInteger MAX_SQRT_RATIO = new BigInteger("1461446703485210103287273052203988822378723970342");
    public static final BigInteger MAX_UINT256 = TWO.pow(256).subtract(ONE);
}
