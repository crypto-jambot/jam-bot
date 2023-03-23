package dev.rabbit.utils;

import dev.rabbit.contracts.Arbitrage;
import dev.rabbit.generated.UniswapV2Pair;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint112;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.web3j.utils.Convert.Unit;

@AllArgsConstructor(access = AccessLevel.NONE)
public class Constants {
    public static final Function V2_GET_RESERVES_FN = new Function(UniswapV2Pair.FUNC_GETRESERVES, List.of(), List.of(
            new TypeReference<Uint112>() {},
            new TypeReference<Uint112>() {},
            new TypeReference<Uint32>() {}
    ));
    public static final String V2_GET_RESERVES_ENCODED_FN = FunctionEncoder.encode(V2_GET_RESERVES_FN);
    public static Function getJamExecuteTradeFn(List<String> tokenPath, List<String> poolPath, BigInteger tradeAmount) {
        return new Function(
                Arbitrage.FUNC_EXECUTETRADE,
                Arrays.asList(
                        new DynamicArray<>(Address.class, org.web3j.abi.Utils.typeMap(tokenPath, Address.class)),
                        new DynamicArray<>(Address.class, org.web3j.abi.Utils.typeMap(poolPath, Address.class)),
                        new Uint256(tradeAmount)),
                Collections.emptyList());
    }
    public static final String NULL_ADDRESS = "0x0000000000000000000000000000000000000000";
    public static final String JAM_BOT_ADDRESS = "0xB56af9ACd06692BC2E1a45C8b6D5ee7D59A8f6C3";
    public static final String WETH_ADDRESS = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2";
    public static final String DAI_ADDRESS = "0x6B175474E89094C44Da98b954EedeAC495271d0F";
    public static final String USDC_ADDRESS = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";
    public static final BigDecimal R1 = new BigDecimal("0.997");
    public static final BigDecimal R2 = BigDecimal.ONE;
    public static Map<String, Unit> conversions = Map.of(
            "WETH", Unit.ETHER,
            "USDC", Unit.MWEI,
            "USDT", Unit.MWEI,
            "DAI", Unit.ETHER,
            "UNI", Unit.ETHER,
            "SOTO", Unit.ETHER,
            "ICHI", Unit.ETHER,
            "LINK", Unit.ETHER,
            "SUSHI", Unit.ETHER
    );
}
