package dev.rabbit.pool;

import dev.rabbit.record.Tree;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthCall;

import java.math.BigInteger;
import java.util.List;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class Pool<T extends State> {
    protected final String exchangeName;
    protected final int fee;
    @EqualsAndHashCode.Include
    protected final String address;
    protected T state;

    protected Pool(Tree.Swap.Exchange exchange) {
        this.exchangeName = exchange.name();
        this.fee = exchange.fee();
        this.address = exchange.pool();
    }

    public abstract List<Request<?, EthCall>> getRequestsForData();
    public abstract List<Request<?, EthCall>> getSecondaryRequestsForData();
    public abstract void assignData(List<Response<?>> responses);
    public abstract void assignSecondaryData(List<Response<?>> responses);
    public abstract BigInteger getReserve0();
    public abstract BigInteger getReserve1();
    public abstract BigInteger computeAmountOutNoTicks(BigInteger amountIn, boolean ordered);
    public abstract BigInteger computeAmountInNoTicks(BigInteger amountOut, boolean ordered);
    public abstract BigInteger computeAmountOutWithTicks(BigInteger amountIn, boolean ordered);
    public abstract BigInteger getFullInputAmountOrToFirstTick(BigInteger maxAmount, boolean ordered);

    /**
     * @param amountIn The amount of tokens going into the swap. Equivalent to amountSpecified (positive) in the V3 contract swap fn
     * @param ordered Whether the swap is inputting token0 for some output of token1 or vise versa. Equivalent to zeroForOne in the V3 contract swap fn
     * @param updateState Whether to update the state of the pool after the swap
     * @return amountOut from the swap
     */
    public abstract BigInteger swap(BigInteger amountIn, boolean ordered, boolean updateState);
    public abstract Function getSwapFn(Address recipient, boolean ordered, BigInteger amountIn, BigInteger amountOut, byte[] bytes);
}
