package dev.rabbit.pool;

import com.google.cloud.Tuple;
import com.google.common.base.Preconditions;
import dev.rabbit.exception.RPCCallFailureException;
import dev.rabbit.generated.IUniswapV3Pool;
import dev.rabbit.record.Tree;
import dev.rabbit.spring.SpringContext;
import dev.rabbit.utils.Wallet;
import dev.rabbit.utils.v3.FullMath;
import dev.rabbit.utils.v3.SqrtPriceMath;
import dev.rabbit.utils.v3.TickMath;
import lombok.Builder;
import lombok.Data;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Int24;
import org.web3j.abi.datatypes.generated.Uint128;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint160;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.rabbit.utils.Constants.MAX_TICK;
import static dev.rabbit.utils.Constants.MIN_TICK;
import static dev.rabbit.utils.Constants.ONE_MILLION_BI;
import static dev.rabbit.utils.v3.BitMath.leastSignificantBit;
import static dev.rabbit.utils.v3.BitMath.mostSignificantBit;
import static dev.rabbit.utils.Constants.NULL_ADDRESS;
import static dev.rabbit.utils.Constants.Q96;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

public class UniswapV3Pool extends Pool {

    private final IUniswapV3Pool contract;
    private final int tickSpacing;
    private BigInteger liquidity;
    private BigInteger sqrtPriceX96;
    private BigDecimal sqrtPrice;
    private BigInteger tick;
    private boolean useCache;
    private SwapCache cache;
    private final Map<BigInteger, BigInteger> tickBitmap = new HashMap<>();
    public static final Function V3_LIQUIDITY_FN = new Function(IUniswapV3Pool.FUNC_LIQUIDITY, List.of(), List.of(new TypeReference<Uint128>() {}));
    public static final String V3_LIQUIDITY_ENCODED_FN = FunctionEncoder.encode(V3_LIQUIDITY_FN);
    public static final Function V3_SLOT0_FN = new Function(IUniswapV3Pool.FUNC_SLOT0, List.of(), List.of(
            new TypeReference<Uint160>() {},
            new TypeReference<Int24>() {},
            new TypeReference<Uint16>() {},
            new TypeReference<Uint16>() {},
            new TypeReference<Uint16>() {},
            new TypeReference<Uint8>() {},
            new TypeReference<Bool>() {}
    ));
    public static final String V3_SLOT0_ENCODED_FN = FunctionEncoder.encode(V3_SLOT0_FN);
    public UniswapV3Pool(Tree.Swap.Exchange exchange) {
        super(exchange);
        tickSpacing = exchange.tickSpacing();
        contract = IUniswapV3Pool.load(address, SpringContext.getBean(Web3j.class), SpringContext.getBean(Wallet.class).getAccount(0).getCredentials(), new StaticGasProvider(ZERO, ZERO));
        useCache = false;
    }

    @Override
    public List<Request<?, EthCall>> getRequestsForData() {
        Web3j web3j = SpringContext.getBean(Web3j.class);
        return List.of(
                web3j.ethCall(Transaction.createEthCallTransaction(NULL_ADDRESS, address, V3_LIQUIDITY_ENCODED_FN), DefaultBlockParameterName.LATEST),
                web3j.ethCall(Transaction.createEthCallTransaction(NULL_ADDRESS, address, V3_SLOT0_ENCODED_FN), DefaultBlockParameterName.LATEST));
    }

    @Override
    public void assignData(List<Response<?>> responses) {
        Response<?> liquidityResponse = responses.get(0);
        List<Type> liquidityOutputTypes = FunctionReturnDecoder.decode((String) liquidityResponse.getResult(), V3_LIQUIDITY_FN.getOutputParameters());
        setLiquidity((BigInteger) liquidityOutputTypes.get(0).getValue());

        Response<?> slot0Response = responses.get(1);
        List<Type> slot0OutputTypes = FunctionReturnDecoder.decode((String) slot0Response.getResult(), V3_SLOT0_FN.getOutputParameters());
        setSqrtPriceX96((BigInteger) slot0OutputTypes.get(0).getValue());
        setSqrtPrice(new BigDecimal(getSqrtPriceX96()).divide(new BigDecimal(Q96), 30, RoundingMode.HALF_DOWN));
        setTick((BigInteger) slot0OutputTypes.get(1).getValue());
    }

    @Override
    public BigInteger getReserve0() {
        return new BigDecimal(getLiquidity()).divide(getSqrtPrice(), 30, RoundingMode.HALF_DOWN).toBigInteger();
    }

    @Override
    public BigInteger getReserve1() {
        return new BigDecimal(getLiquidity()).multiply(getSqrtPrice()).toBigInteger();
    }

    @Override
    public BigInteger getAmountOutAssumingNoTickCrossings(BigInteger amountIn, boolean ordered) {
        BigInteger amountInAfterFee = FullMath.mulDiv(amountIn, ONE_MILLION_BI.subtract(BigInteger.valueOf(fee)), ONE_MILLION_BI);
        BigInteger sqrtRatioNextX96 = SqrtPriceMath.getNextSqrtPriceFromInput(getSqrtPriceX96(), getLiquidity(), amountInAfterFee, ordered);

        BigInteger trueAmountIn, trueAmountOut;
        if (ordered) {
            trueAmountIn = SqrtPriceMath.getAmount0Delta(sqrtRatioNextX96, getSqrtPriceX96(), getLiquidity(), true);
            trueAmountOut = SqrtPriceMath.getAmount1Delta(sqrtRatioNextX96, getSqrtPriceX96(), getLiquidity(), false);
        } else {
            trueAmountIn = SqrtPriceMath.getAmount1Delta(getSqrtPriceX96(), sqrtRatioNextX96, getLiquidity(), true);
            trueAmountOut = SqrtPriceMath.getAmount0Delta(getSqrtPriceX96(), sqrtRatioNextX96, getLiquidity(), false);
        }
        return trueAmountOut;
    }

    @Override
    public BigInteger getAmountInAssumingNoTickCrossings(BigInteger amountOut, boolean ordered) {
        return ZERO;
    }

    @Override
    public BigInteger getFullInputAmountOrToFirstTick(BigInteger maxAmount, boolean ordered) {
        BigInteger currentTick = getTick();
        BigInteger currentSqrtPriceX96 = getSqrtPriceX96();
        BigInteger amountRemaining = new BigInteger(maxAmount.toString());
        BigInteger inputAmount = BigInteger.valueOf(0);

        while (true) {
            Tuple<BigInteger, Boolean> next = nextInitializedTickWithinOneWord(ordered, currentTick);
            BigInteger nextTick = next.x();
            boolean initialized = next.y();

            // ensure that we do not overshoot the min/max tick
            if (nextTick.compareTo(MIN_TICK) < 0) {
                nextTick = MIN_TICK;
            } else if (nextTick.compareTo(MAX_TICK) > 0) {
                nextTick = MAX_TICK;
            }

            BigInteger sqrtRatioAtNextTick = TickMath.getSqrtRatioAtTick(nextTick);
            BigInteger amountInToNextTick = ordered
                    ? SqrtPriceMath.getAmount0Delta(sqrtRatioAtNextTick, currentSqrtPriceX96, getLiquidity(), true)
                    : SqrtPriceMath.getAmount1Delta(currentSqrtPriceX96, sqrtRatioAtNextTick, getLiquidity(), true);
            BigInteger amountInToNextTickPlusFee = FullMath.mulDivRoundingUp(amountInToNextTick, ONE_MILLION_BI, ONE_MILLION_BI.subtract(BigInteger.valueOf(fee)));

            if (amountRemaining.compareTo(amountInToNextTickPlusFee) < 0) {
                return inputAmount.add(amountRemaining);
            }

            if (initialized) {
                return inputAmount.add(amountInToNextTickPlusFee);
            }

            inputAmount = inputAmount.add(amountInToNextTickPlusFee);
            amountRemaining = amountRemaining.subtract(amountInToNextTickPlusFee);
            currentTick = nextTick;
            currentSqrtPriceX96 = sqrtRatioAtNextTick;
        }
    }

    private Tuple<BigInteger, Boolean> nextInitializedTickWithinOneWord(boolean lte, BigInteger tick) {
        BigInteger compressed = tick.divide(BigInteger.valueOf(tickSpacing));
        if (tick.compareTo(ZERO) < 0 && tick.mod(BigInteger.valueOf(tickSpacing)).compareTo(ZERO) != 0) compressed = compressed.subtract(ONE);

        BigInteger next;
        boolean initialized;

        if (lte) {
            Tuple<BigInteger, BigInteger> position = position(compressed);
            BigInteger wordPosition = position.x();
            int bitPosition = position.y().intValue();

            // all the 1s at or to the right of the current bitPosition
            BigInteger mask = ONE.shiftLeft(bitPosition).subtract(ONE).add(ONE.shiftLeft(bitPosition));
            BigInteger masked = getBitmapValue(wordPosition).and(mask);

            // if there are no initialized ticks to the right of or at the current tick, return rightmost in the word
            initialized = masked.compareTo(ZERO) != 0;

            next = initialized
                    ? compressed.subtract(BigInteger.valueOf(bitPosition - mostSignificantBit(masked))).multiply(BigInteger.valueOf(tickSpacing))
                    : compressed.subtract(BigInteger.valueOf(bitPosition)).multiply(BigInteger.valueOf(tickSpacing));

        } else {
            Tuple<BigInteger, BigInteger> position = position(compressed.add(ONE));
            BigInteger wordPosition = position.x();
            int bitPosition = position.y().intValue();

            // all the 1s at or to the left of the bitPosition
            BigInteger mask = ONE.shiftLeft(bitPosition).subtract(ONE).not();
            BigInteger masked = getBitmapValue(wordPosition).and(mask);

            // if there are no initialized ticks to the right of or at the current tick, return rightmost in the word
            initialized = masked.compareTo(ZERO) != 0;

            next = initialized
                    ? compressed.add(ONE).add(BigInteger.valueOf(leastSignificantBit(masked) - bitPosition)).multiply(BigInteger.valueOf(tickSpacing))
                    : compressed.add(ONE).add(BigInteger.valueOf(255 - bitPosition)).multiply(BigInteger.valueOf(tickSpacing));
        }

        return Tuple.of(next, initialized);
    }

    private Tuple<BigInteger, BigInteger> position(BigInteger tick) {
        return Tuple.of(
                tick.shiftRight(8),
                tick.mod(BigInteger.valueOf(256)));
    }

    private BigInteger getBitmapValue(BigInteger wordPosition) {
        if (!tickBitmap.containsKey(wordPosition)) {
            try {
                tickBitmap.put(wordPosition, contract.tickBitmap(wordPosition).send());
            } catch (Exception exception) {
                throw RPCCallFailureException.of("Failure to get a value from the tick bitmap", exception);
            }
        }
        return tickBitmap.get(wordPosition);
    }

    private BigInteger getLiquidity() {
        return useCache ? cache.liquidity : liquidity;
    }
    private BigInteger getSqrtPriceX96() {
        return useCache ? cache.sqrtPriceX96 : sqrtPriceX96;
    }
    private BigDecimal getSqrtPrice() {
        return useCache ? cache.sqrtPrice : sqrtPrice;
    }
    private BigInteger getTick() {
        return useCache ? cache.tick : tick;
    }
    private void setLiquidity(BigInteger liquidity) {
        if (useCache) {
            cache.setLiquidity(liquidity);
            return;
        }
        Preconditions.checkState(this.liquidity == null, "Cannot only set value if not initialized.");
        this.liquidity = liquidity;
    }
    private void setSqrtPriceX96(BigInteger sqrtPriceX96) {
        if (useCache) {
            cache.setSqrtPriceX96(sqrtPriceX96);
            return;
        }
        Preconditions.checkState(this.sqrtPriceX96 == null, "Cannot only set value if not initialized.");
        this.sqrtPriceX96 = sqrtPriceX96;
    }
    private void setSqrtPrice(BigDecimal sqrtPrice) {
        if (useCache) {
            cache.setSqrtPrice(sqrtPrice);
            return;
        }
        Preconditions.checkState(this.sqrtPrice == null, "Cannot only set value if not initialized.");
        this.sqrtPrice = sqrtPrice;
    }
    private void setTick(BigInteger tick) {
        if (useCache) {
            cache.setTick(tick);
            return;
        }
        Preconditions.checkState(this.tick == null, "Cannot only set value if not initialized.");
        this.tick = tick;
    }
    @Override
    public void initializeCache() {
        cache = SwapCache.builder()
                .liquidity(liquidity)
                .sqrtPriceX96(sqrtPriceX96)
                .sqrtPrice(sqrtPrice)
                .tick(tick)
                .build();
        useCache = true;
    }
    @Override
    public void clearCache() {
        cache = null;
        useCache = false;
    }
    @Data
    @Builder
    private static class SwapCache {
        private BigInteger liquidity;
        private BigInteger sqrtPriceX96;
        private BigDecimal sqrtPrice;
        private BigInteger tick;
    }
}
