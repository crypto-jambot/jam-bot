package dev.rabbit.pool;

import dev.rabbit.generated.IUniswapV2Pair;
import dev.rabbit.record.Tree;
import dev.rabbit.spring.SpringContext;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint112;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;

import java.math.BigInteger;
import java.util.List;

import static dev.rabbit.utils.Constants.NULL_ADDRESS;
import static java.math.BigInteger.ONE;

public class UniswapV2Pool extends Pool {

    private BigInteger reserve0;
    private BigInteger reserve1;
    public static final Function V2_GET_RESERVES_FN = new Function(IUniswapV2Pair.FUNC_GETRESERVES, List.of(), List.of(
            new TypeReference<Uint112>() {},
            new TypeReference<Uint112>() {},
            new TypeReference<Uint32>() {}
    ));
    public static final String V2_GET_RESERVES_ENCODED_FN = FunctionEncoder.encode(V2_GET_RESERVES_FN);
    public UniswapV2Pool(Tree.Swap.Exchange exchange) {
        super(exchange);
    }

    @Override
    public List<Request<?, EthCall>> getRequestsForData() {
        Web3j web3j = SpringContext.getBean(Web3j.class);
        return List.of(web3j.ethCall(Transaction.createEthCallTransaction(NULL_ADDRESS, address, V2_GET_RESERVES_ENCODED_FN), DefaultBlockParameterName.LATEST));
    };
    @Override
    public void assignData(List<Response<?>> responses) {
        responses.forEach(response -> {
            List<Type> type = FunctionReturnDecoder.decode((String) response.getResult(), V2_GET_RESERVES_FN.getOutputParameters());
            reserve0 = (BigInteger) type.get(0).getValue();
            reserve1 = (BigInteger) type.get(1).getValue();
        });
    }

    @Override
    public BigInteger getReserve0() {
        return reserve0;
    }

    @Override
    public BigInteger getReserve1() {
        return reserve1;
    }

    @Override
    public BigInteger getAmountOutAssumingNoTickCrossings(BigInteger amountIn, boolean ordered) {
        BigInteger reserveIn = ordered ? getReserve0() : getReserve1();
        BigInteger reserveOut = ordered ? getReserve1() : getReserve0();
        BigInteger amountInWithFee = amountIn.multiply(BigInteger.valueOf(10000 - (fee/100)));
        BigInteger numerator = amountInWithFee.multiply(reserveOut);
        BigInteger denominator = reserveIn.multiply(new BigInteger("10000")).add(amountInWithFee);
        return numerator.divide(denominator);
    }

    @Override
    public BigInteger getAmountInAssumingNoTickCrossings(BigInteger amountOut, boolean ordered) {
        BigInteger reserveIn = ordered ? getReserve0() : getReserve1();
        BigInteger reserveOut = ordered ? getReserve1() : getReserve0();
        BigInteger numerator = reserveIn.multiply(amountOut).multiply(BigInteger.valueOf(10000));
        BigInteger denominator = reserveOut.subtract(amountOut).multiply(BigInteger.valueOf(10000 - (fee/100)));
        BigInteger result = numerator.divide(denominator).add(ONE);
        return result;
    }
}
