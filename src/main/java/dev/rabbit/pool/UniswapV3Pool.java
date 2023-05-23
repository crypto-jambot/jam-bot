package dev.rabbit.pool;

import com.google.cloud.Tuple;
import dev.rabbit.exception.RPCCallFailureException;
import dev.rabbit.generated.IUniswapV3Pool;
import dev.rabbit.record.Tree;
import dev.rabbit.spring.SpringContext;
import dev.rabbit.utils.Wallet;
import dev.rabbit.utils.v3.FullMath;
import dev.rabbit.utils.v3.LiquidityMath;
import dev.rabbit.utils.v3.SqrtPriceMath;
import dev.rabbit.utils.v3.TickMath;
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
import org.web3j.tuples.generated.Tuple8;
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

public class UniswapV3Pool extends Pool<V3State> {

    private final IUniswapV3Pool contract;
    private final int tickSpacing;
    private final Map<BigInteger, BigInteger> tickBitmap = new HashMap<>();

    /**
     * <h2>This map stores information for each individual tick.
     * <p>The key is a BigInteger representing the tick value.
     * <p>The value is a Tuple8 object containing the following information:
     * <ul>
     * <li><code>liquidityGross (BigInteger)</code> - The total position liquidity that references this tick.
     * <li><code>liquidityNet (BigInteger)</code> - The amount of net liquidity added (subtracted) when the tick is crossed
     *   from left to right (right to left).
     * <li><code>feeGrowthOutside0X128 (BigInteger)</code> - The fee growth per unit of liquidity on the _other_ side of this tick
     *   (relative to the current tick). This value only has relative meaning, not absolute, and depends on
     *   when the tick is initialized.
     * <li><code>feeGrowthOutside1X128 (BigInteger)</code> - The fee growth per unit of liquidity on the _other_ side of this tick
     *   (relative to the current tick). This value only has relative meaning, not absolute, and depends on
     *   when the tick is initialized.
     * <li><code>tickCumulativeOutside (BigInteger)</code> - The cumulative tick value on the other side of the tick.
     * <li><code>secondsPerLiquidityOutsideX128 (BigInteger)</code> - The seconds per unit of liquidity on the _other_ side of this tick
     *   (relative to the current tick). This value only has relative meaning, not absolute, and depends on
     *   when the tick is initialized.
     * <li><code>secondsOutside (BigInteger)</code> - The seconds spent on the other side of the tick (relative to the current tick).
     *   This value only has relative meaning, not absolute, and depends on when the tick is initialized.
     * <li><code>initialized (Boolean)</code> - Represents whether the tick is initialized. It is true if the tick is initialized,
     *   i.e., the value is exactly equivalent to the expression liquidityGross != 0. These 8 bits are set to prevent
     *   fresh stores when crossing newly initialized ticks.
     * </ul>
     */
    private final Map<BigInteger, Tuple8<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, Boolean>> ticks = new HashMap<>();
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
        state = new V3State();
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
        state.setLiquidity((BigInteger) liquidityOutputTypes.get(0).getValue());

        Response<?> slot0Response = responses.get(1);
        List<Type> slot0OutputTypes = FunctionReturnDecoder.decode((String) slot0Response.getResult(), V3_SLOT0_FN.getOutputParameters());
        state.setSqrtPriceX96((BigInteger) slot0OutputTypes.get(0).getValue());
        state.setTick((BigInteger) slot0OutputTypes.get(1).getValue());
    }

    @Override
    public BigInteger getReserve0() {
        return new BigDecimal(state.getLiquidity()).divide(state.getSqrtPrice(), 30, RoundingMode.HALF_DOWN).toBigInteger();
    }

    @Override
    public BigInteger getReserve1() {
        return new BigDecimal(state.getLiquidity()).multiply(state.getSqrtPrice()).toBigInteger();
    }

    @Override
    public BigInteger computeAmountOutNoTicks(BigInteger amountIn, boolean ordered) {
        BigInteger amountInAfterFee = FullMath.mulDiv(amountIn, ONE_MILLION_BI.subtract(BigInteger.valueOf(fee)), ONE_MILLION_BI);
        BigInteger sqrtRatioNextX96 = SqrtPriceMath.getNextSqrtPriceFromInput(state.getSqrtPriceX96(), state.getLiquidity(), amountInAfterFee, ordered);

        return ordered
                ? SqrtPriceMath.getAmount1Delta(sqrtRatioNextX96, state.getSqrtPriceX96(), state.getLiquidity(), false)
                : SqrtPriceMath.getAmount0Delta(state.getSqrtPriceX96(), sqrtRatioNextX96, state.getLiquidity(), false);
    }

    @Override
    public BigInteger computeAmountInNoTicks(BigInteger amountOut, boolean ordered) {
        BigInteger sqrtRatioNextX96 = SqrtPriceMath.getNextSqrtPriceFromOutput(state.getSqrtPriceX96(), state.getLiquidity(), amountOut, ordered);

        BigInteger amountIn = ordered
                ? SqrtPriceMath.getAmount0Delta(sqrtRatioNextX96, state.getSqrtPriceX96(), state.getLiquidity(), true)
                : SqrtPriceMath.getAmount1Delta(state.getSqrtPriceX96(), sqrtRatioNextX96, state.getLiquidity(), true);

        return FullMath.mulDivRoundingUp(amountIn, ONE_MILLION_BI, ONE_MILLION_BI.subtract(BigInteger.valueOf(fee)));
    }

    @Override
    public BigInteger computeAmountOutWithTicks(BigInteger amountIn, boolean ordered) {
        return null;
    }

    @Override
    public BigInteger computeAmountInWithTicks(BigInteger amountOut, boolean ordered) {
        return null;
    }

    @Override
    public BigInteger getFullInputAmountOrToFirstTick(BigInteger maxAmount, boolean ordered) {
        BigInteger currentTick = state.getTick();
        BigInteger currentSqrtPriceX96 = state.getSqrtPriceX96();
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
                    ? SqrtPriceMath.getAmount0Delta(sqrtRatioAtNextTick, currentSqrtPriceX96, state.getLiquidity(), true)
                    : SqrtPriceMath.getAmount1Delta(currentSqrtPriceX96, sqrtRatioAtNextTick, state.getLiquidity(), true);
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

    @Override
    public BigInteger swap(BigInteger amountIn, boolean ordered) {
        BigInteger amountRemaining = amountIn;
        BigInteger amountCalculated = ZERO;
        BigInteger currentLiquidity = state.getLiquidity();
        BigInteger currentTick = state.getTick();
        BigInteger currentSqrtPriceX96 = state.getSqrtPriceX96();

        while (amountRemaining.compareTo(ZERO) > 0) {
            BigInteger sqrtPriceStartX96 = currentSqrtPriceX96;
            Tuple<BigInteger, Boolean> next = nextInitializedTickWithinOneWord(ordered, currentTick);
            BigInteger nextTick = next.x();
            boolean initialized = next.y();

            // ensure that we do not overshoot the min/max tick
            if (nextTick.compareTo(MIN_TICK) < 0) {
                nextTick = MIN_TICK;
            } else if (nextTick.compareTo(MAX_TICK) > 0) {
                nextTick = MAX_TICK;
            }

            BigInteger sqrtPriceNextX96 = TickMath.getSqrtRatioAtTick(nextTick);
            SwapStep swapStep = computeSwapStep(currentSqrtPriceX96, sqrtPriceNextX96, currentLiquidity, amountRemaining, ordered);
            currentSqrtPriceX96 = swapStep.sqrtRatioNextX96();

            amountRemaining = amountRemaining.subtract(swapStep.amountIn().add(swapStep.feeAmount()));
            amountCalculated = amountCalculated.subtract(swapStep.amountOut());

            // shift tick if we reached the next price
            if (currentSqrtPriceX96.equals(sqrtPriceNextX96)) {
                // if the tick is initialized, run the tick transition
                if (initialized) {
                    BigInteger liquidityNet = crossTicks(nextTick);
                    // if we're moving leftward, we interpret liquidityNet as the opposite sign
                    if (ordered) liquidityNet = liquidityNet.negate();
                    currentLiquidity = LiquidityMath.addDelta(currentLiquidity, liquidityNet);
                }
                currentTick = ordered ? nextTick.subtract(ONE) : nextTick;
            } else if (!currentSqrtPriceX96.equals(sqrtPriceStartX96)) {
                // recompute unless we're on a lower tick boundary (i.e. already transitioned ticks), and haven't moved
                currentTick = TickMath.getTickAtSqrtRatio(currentSqrtPriceX96);
            }
        }
        state.setTick(currentTick);
        state.setLiquidity(currentLiquidity);
        state.setSqrtPriceX96(currentSqrtPriceX96);
        return amountCalculated.negate();
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

    private Tuple8<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, Boolean> getTick(BigInteger tick) {
        if (!ticks.containsKey(tick)) {
            try {
                ticks.put(tick, contract.ticks(tick).send());
            } catch (Exception exception) {
                throw RPCCallFailureException.of("Failure to get a value from the ticks mapping", exception);
            }
        }
        return ticks.get(tick);
    }

    private SwapStep computeSwapStep(BigInteger sqrtRatioCurrentX96, BigInteger sqrtRatioTargetX96, BigInteger liquidity, BigInteger amountRemaining, boolean ordered) {
        BigInteger amountRemainingAfterFee = FullMath.mulDiv(amountRemaining, ONE_MILLION_BI.subtract(BigInteger.valueOf(fee)), ONE_MILLION_BI);

        BigInteger amountIn = ordered
                ? SqrtPriceMath.getAmount0Delta(sqrtRatioTargetX96, sqrtRatioCurrentX96, liquidity, true)
                : SqrtPriceMath.getAmount1Delta(sqrtRatioCurrentX96, sqrtRatioTargetX96, liquidity, true);

        BigInteger sqrtRatioNextX96 = amountRemainingAfterFee.compareTo(amountIn) >= 0
                ? sqrtRatioTargetX96
                : SqrtPriceMath.getNextSqrtPriceFromInput(sqrtRatioCurrentX96, liquidity, amountRemainingAfterFee, ordered);

        if (!sqrtRatioTargetX96.equals(sqrtRatioNextX96)) {
            amountIn = ordered
                    ? SqrtPriceMath.getAmount0Delta(sqrtRatioNextX96, sqrtRatioCurrentX96, liquidity, true)
                    : SqrtPriceMath.getAmount1Delta(sqrtRatioCurrentX96, sqrtRatioNextX96, liquidity, true);
        }
        BigInteger amountOut = ordered
                ? SqrtPriceMath.getAmount1Delta(sqrtRatioNextX96, sqrtRatioCurrentX96, liquidity, false)
                : SqrtPriceMath.getAmount0Delta(sqrtRatioCurrentX96, sqrtRatioNextX96, liquidity, false);
        BigInteger feeAmount = !sqrtRatioTargetX96.equals(sqrtRatioNextX96)
                ? amountRemaining.subtract(amountIn)
                : FullMath.mulDivRoundingUp(amountIn, BigInteger.valueOf(fee), ONE_MILLION_BI.subtract(BigInteger.valueOf(fee)));
        return new SwapStep(sqrtRatioNextX96, amountIn, amountOut, feeAmount);
    }
    private BigInteger crossTicks(BigInteger nextTick) {
        return getTick(nextTick).component2();
    }
}
record SwapStep(BigInteger sqrtRatioNextX96, BigInteger amountIn, BigInteger amountOut, BigInteger feeAmount) {
}
