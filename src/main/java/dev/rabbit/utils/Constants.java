package dev.rabbit.utils;

import dev.rabbit.generated.UniswapV2Pair;
import dev.rabbit.pojo.Path;
import dev.rabbit.record.Reserves;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint112;
import org.web3j.abi.datatypes.generated.Uint32;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static org.web3j.utils.Convert.Unit;

@AllArgsConstructor(access = AccessLevel.NONE)
public class Constants {
    public static final Function V2_GET_RESERVES_FN = new Function(UniswapV2Pair.FUNC_GETRESERVES, List.of(), List.of(
            new TypeReference<Uint112>() {},
            new TypeReference<Uint112>() {},
            new TypeReference<Uint32>() {}
    ));
    public static final String V2_GET_RESERVES_ENCODED_FN = FunctionEncoder.encode(V2_GET_RESERVES_FN);

    public static final String NULL_ADDRESS = "0x0000000000000000000000000000000000000000";

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
