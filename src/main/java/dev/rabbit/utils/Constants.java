package dev.rabbit.utils;

import dev.rabbit.generated.UniswapV2Pair;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint112;
import org.web3j.abi.datatypes.generated.Uint32;

import java.util.Arrays;
import java.util.List;

@AllArgsConstructor(access = AccessLevel.NONE)
public class Constants {
    public static final Function V2_GET_RESERVES_FUNCTION = new Function(UniswapV2Pair.FUNC_GETRESERVES, List.of(), List.of(
            new TypeReference<Uint112>() {},
            new TypeReference<Uint112>() {},
            new TypeReference<Uint32>() {}
    ));
    public static final String V2_GET_RESERVES_ENCODED_FUNCTION = FunctionEncoder.encode(V2_GET_RESERVES_FUNCTION);

    public static final String NULL_ADDRESS = "0x0000000000000000000000000000000000000000";
}
