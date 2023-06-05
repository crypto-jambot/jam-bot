package dev.rabbit.pojo;

import com.google.cloud.Tuple;
import com.google.common.base.Preconditions;
import dev.rabbit.exception.EthPriceRequestFailureException;
import dev.rabbit.exception.GasEstimationFailureException;
import dev.rabbit.exception.RPCCallFailureException;
import dev.rabbit.exception.ZeroAmountException;
import dev.rabbit.pool.Pool;
import dev.rabbit.pool.UniswapV2Pool;
import dev.rabbit.pool.UniswapV3Pool;
import dev.rabbit.record.Tree;
import dev.rabbit.spring.SpringContext;
import dev.rabbit.utils.ValueConverter;
import dev.rabbit.utils.Wallet;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.ObjectUtils;
import org.web3j.abi.DefaultFunctionEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import static dev.rabbit.utils.Constants.DEPLOYER_ADDRESS;
import static dev.rabbit.utils.Constants.JAM_BOT_V3_ADDRESS;
import static dev.rabbit.utils.Constants.ONE_GWEI;
import static dev.rabbit.utils.Constants.R2;
import static dev.rabbit.utils.Constants.feeToPercent;
import static dev.rabbit.utils.Constants.getERC20TransferFn;
import static dev.rabbit.utils.Constants.getJamExecuteTradeFn;
import static java.math.BigInteger.ZERO;

@Slf4j
@Getter
public class Path implements Comparable<Path> {
    private final LinkedList<Trade> trades = new LinkedList<>();
    private BigInteger optimalTradingVolume = ZERO;
    private BigInteger profitBeforeGas;
    private BigDecimal profitBeforeGasInEther;
    private byte[] callData;
    @Setter
    private BigInteger baseFeePerGas;
    @Setter
    private BigInteger maxBaseFeeInFutureBlock;
    @Setter
    private BigInteger maxPriorityFeePerGas;
    private BigInteger gasUsed;
    private BigInteger netProfitAfterBaseFee;
    private BigInteger netProfit;
    private String signedTransaction;
    public static Path of() {
        return new Path();
    }
    public static Path of(Path path) {
        Path newPath = new Path();
        path.trades.forEach(newPath::addTrade);
        return newPath;
    }

    /**
     * Add a trade to this path.
     * @param start The Start Node. This will be mapped to the Start token in the trade object. It will create a new object.
     * @param end The End Node. This will be mapped to the End token in the trade object. It will create a new object.
     * @param pool This will be mapped to the Pool object in the trade object. It will be the same reference to the underlying Pool.
     * @param ordered Whether this trade is ordered or not.
     */
    public void addTrade(Tree.Node start, Tree.Node end, Pool pool, boolean ordered) {
        trades.add(Trade.builder()
                .start(Token.of(start))
                .end(Token.of(end))
                .pool(pool)
                .ordered(ordered)
                .build());
    }

    /**
     * Adds a trade to this path.
     * @param trade A new Trade will be built and added to this path.
     *              The pool field is assigned via refernce to the newly built trade.
     */
    private void addTrade(Trade trade) {
        trades.add(Trade.builder()
                .start(Token.of(trade.getStart()))
                .end(Token.of(trade.getEnd()))
                .pool(trade.getPool())
                .ordered(trade.isOrdered())
                .build());
    }

    public void removeLast() {
        this.trades.removeLast();
    }

    /**
     * Runs a top level equation to check if the multiplied reserves of all pools are greater than the fees that would be incurred from a Path swap
     * @return True if value is extractable from this Path. False otherwise
     * @see <a href="https://arxiv.org/pdf/2105.02784.pdf">Cyclic Arbitrage in Decentralized Exchanges</a>
     */
    public boolean isValueExtractable() {
        BigInteger leftOperandNumerator = BigInteger.ONE;
        BigInteger leftOperandDenominator = BigInteger.ONE;
        BigDecimal rightOperandNumerator = BigDecimal.ONE;

        BigDecimal multipliedInputFees = BigDecimal.ONE;
        BigDecimal multipliedOutputFees = BigDecimal.ONE;

        for (Trade trade : this.trades) {
            BigInteger reserve0 = trade.getPool().getReserve0();
            BigInteger reserve1 = trade.getPool().getReserve1();

            if (reserve0.compareTo(ZERO) <= 0 || reserve1.compareTo(ZERO) <= 0) return false;

            if (trade.isOrdered()) {
                leftOperandNumerator = leftOperandNumerator.multiply(reserve1);
                leftOperandDenominator = leftOperandDenominator.multiply(reserve0);
            } else {
                leftOperandNumerator = leftOperandNumerator.multiply(reserve0);
                leftOperandDenominator = leftOperandDenominator.multiply(reserve1);
            }

            multipliedInputFees = multipliedInputFees.multiply(feeToPercent(trade.getPool().getFee()));
            // Constant as of present time. Could be changed in future
            multipliedOutputFees = multipliedOutputFees.multiply(R2);
        }

        BigDecimal rightOperandDenominator = multipliedInputFees.multiply(multipliedOutputFees);

        BigDecimal leftOperand = new BigDecimal(leftOperandNumerator).divide(new BigDecimal(leftOperandDenominator), MathContext.DECIMAL128);
        BigDecimal rightOperand = rightOperandNumerator.divide(rightOperandDenominator, MathContext.DECIMAL128);
        return leftOperand.compareTo(rightOperand) > 0;
    }

    /**
     * Calculate the optimal trading volume for this Path.
     * <ol>
     *     <li>Calculate initial optimal amount and trade amounts through the path assuming no tick encounters</li>
     *     <li>Determine the earliest point in the trade path where a tick is encountered</li>
     *     <li>Perform a partial swap up to the tick, and recalculate the state of all the pool reserves</li>
     *     <li>Keep track of the input amount from the partial swap</li>
     *     <li>From the new state, recalculate the optimal amount</li>
     *     <li>Repeat until we complete the full swap. Add up all the input amounts from partial swaps</li>
     *     <li>Assign the final optimalAmount to this Path and return</li>
     * </ol>
     * @throws ZeroAmountException If the initial optimal amount Floors to zero, the path is no good
     * @see <a href="https://arxiv.org/pdf/2105.02784.pdf">Cyclic Arbitrage in Decentralized Exchanges</a>
     */
    public void calculateOptimalTradingVolume() throws ZeroAmountException {
        // Supplier will be reused to recalculate the optimal trading amount
        Supplier<BigDecimal> getOptimalAmountFn = () -> {
            Tuple<BigInteger, BigInteger> orderedReserves = trades.get(0).getOrderedReserves();
            BigDecimal inputReserve = new BigDecimal(orderedReserves.x());
            BigDecimal outputReserve = new BigDecimal(orderedReserves.y());
            int fee = trades.get(0).getPool().getFee();
            for (int i = 1; i < trades.size(); i++) {
                BigDecimal R1 = feeToPercent(trades.get(i).getPool().getFee());

                orderedReserves = trades.get(i).getOrderedReserves();
                BigDecimal ithInputReserve = new BigDecimal(orderedReserves.x());
                BigDecimal ithOutputReserve = new BigDecimal(orderedReserves.y());

                BigDecimal iRNumerator = inputReserve.multiply(ithInputReserve);
                BigDecimal oRNumerator = R1.multiply(R2).multiply(outputReserve).multiply(ithOutputReserve);
                BigDecimal rDenominator = ithInputReserve.add(R1.multiply(R2).multiply(outputReserve));

                inputReserve = iRNumerator.divide(rDenominator, MathContext.DECIMAL128);
                outputReserve = oRNumerator.divide(rDenominator, MathContext.DECIMAL128);
            }
            BigDecimal numerator = feeToPercent(fee).multiply(R2).multiply(outputReserve).multiply(inputReserve).sqrt(MathContext.DECIMAL128).subtract(inputReserve);
            return numerator.divide(feeToPercent(fee), MathContext.DECIMAL128);
        };

        // Determine initial optimal trading amount. This is subject to change if ticks are encountered
        BigInteger optimalInput = getOptimalAmountFn.get().toBigInteger();
        if (optimalInput.equals(ZERO)) throw new ZeroAmountException();

        while (true) {
            BigInteger inputAmountToEarliestTick = null;

            // Populate input and outputs from each trade assuming no tick crossings
            // This computes the initial trade amounts through the path.
            populateInputAndOutputTradeAmountsNoTicks(optimalInput);

            // For each trade, determine whether the input amount is exhausted first or if a tick is encountered
            // If a tick is encountered, we must recalculate the initial input amount into the path to swap up to this tick
            // After iteration, we must find the earliest point where a tick is encountered and perform a partial swap up to the tick
            for (int i = 0; i < trades.size(); i++) {
                Trade trade = trades.get(i);
                BigInteger ithTradeFullInputAmountOrToFirstTick = trade.getFullInputAmountOrToFirstTick();

                // If we didn't encounter a tick, then continue
                if (trade.getAmountIn().equals(ithTradeFullInputAmountOrToFirstTick)) {
                    continue;
                }

                // If we encounter a tick, and it is the first trade, then we already have the initial input required to cross said tick
                if (i == 0) {
                    inputAmountToEarliestTick = ithTradeFullInputAmountOrToFirstTick;
                    continue;
                }

                // If we encounter a tick, and it is not the first trade, then we must recalculate the initial input amount into the path to swap up to this tick
                BigInteger inputAmountToEarliestTickTemp = ithTradeFullInputAmountOrToFirstTick;
                // Recalculate trade inputs to derive the initial input for the path to swap to the tick crossing
                for (int j = i - 1; j >= 0; j--) {
                    inputAmountToEarliestTickTemp = trades.get(j).computeAmountInNoTicks(inputAmountToEarliestTickTemp);
                }

                // Assign the new input amount if it is less than what is currently stored.
                inputAmountToEarliestTick = inputAmountToEarliestTick == null
                        ? inputAmountToEarliestTickTemp
                        : inputAmountToEarliestTick.min(inputAmountToEarliestTickTemp);
            }

            // If inputAmountToEarliestTick is null, that means no ticks were encountered in the path.
            if (inputAmountToEarliestTick == null) {
                optimalTradingVolume = optimalTradingVolume.add(optimalInput);
                return;
            }

            optimalTradingVolume = optimalTradingVolume.add(inputAmountToEarliestTick);

            swap(inputAmountToEarliestTick);

            if (!isValueExtractable()) return;
            optimalInput = getOptimalAmountFn.get().toBigInteger();
            if (optimalInput.equals(ZERO)) return;
        }
    }

    public void populateInputAndOutputTradeAmountsWithTicks() {
        Preconditions.checkState(optimalTradingVolume.compareTo(ZERO) > 0, "Optimal amount must be greater then zero to evaluate for profit");
        trades.get(0).setAmountIn(optimalTradingVolume);
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            trade.computeAndSetAmountOutWithTicks();
            if (i != trades.size() - 1) trades.get(i + 1).setAmountIn(trade.getAmountOut());
        }
    }

    private void populateInputAndOutputTradeAmountsNoTicks(BigInteger initialAmountIn) {
        trades.get(0).setAmountIn(initialAmountIn);
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            trade.computeAndSetAmountOutNoTicks();
            if (i != trades.size() - 1) trades.get(i + 1).setAmountIn(trade.getAmountOut());
        }
    }

    private void swap(BigInteger amountIn) {
        for (Trade trade : trades) {
            amountIn = trade.swap(amountIn);
        }
    }

    public void calculateRevenue() throws EthPriceRequestFailureException {
        ValueConverter valueConverter = SpringContext.getBean(ValueConverter.class);
        profitBeforeGas = trades.getLast().getAmountOut().subtract(trades.getFirst().getAmountIn());
        profitBeforeGasInEther = valueConverter.deriveEtherValue(valueConverter.makeRequest(trades.get(0).getStart()), profitBeforeGas);
    }

    public void encodeCallData() {
        DefaultFunctionEncoder encoder = new DefaultFunctionEncoder();

        List<Address> targetList = new ArrayList<>();
        List<DynamicBytes> payloadList = new ArrayList<>();

        // Encode the first ERC20 transfer and add to the running lists
        targetList.add(new Address(trades.get(0).getStart().getAddress()));
        payloadList.add(new DynamicBytes(Numeric.hexStringToByteArray(encoder.encodeFunction(getERC20TransferFn(trades.get(0).getPool().getAddress(), trades.get(0).getAmountIn())))));

        // Encode all swaps except the last
        for (int i = 0; i < trades.size() - 1; i++) {
            Trade trade = trades.get(i);
            Address sender = new Address(trades.get(i).getPool().getAddress());
            Address recipient = i == trades.size() - 1 ? JAM_BOT_V3_ADDRESS : new Address(trades.get(i + 1).getPool().getAddress());

            // Only logic in app that needs awareness of the concrete type
            if (trade.getPool() instanceof UniswapV2Pool) {

                UniswapV2Pool pool = (UniswapV2Pool) trade.getPool();
                Function swapFn = pool.getSwapFn(trade.isOrdered(), trade.getAmountOut(), recipient, ArrayUtils.EMPTY_BYTE_ARRAY);

                // Add this encoded call to the lists
                targetList.add(sender);
                payloadList.add(new DynamicBytes(Numeric.hexStringToByteArray(encoder.encodeFunction(swapFn))));
                continue;
            }

            // If the trade is a V3 pool, we take the running list of payloads and inject as bytes to the swap
            if (trade.getPool() instanceof  UniswapV3Pool) {

                // Convert the running lists to DynamicArray Web3J type
                DynamicArray<Address> targets = new DynamicArray<>(Address.class, targetList);
                DynamicArray<DynamicBytes> payloads = new DynamicArray<>(DynamicBytes.class, payloadList);

                // Encode to byte[]
                byte[] bytes = Numeric.hexStringToByteArray(encoder.encodeParameters(List.of(targets, payloads)));

                // Clear the lists so avoid double encoding
                targetList.clear();
                payloadList.clear();

                UniswapV3Pool pool = (UniswapV3Pool) trade.getPool();
                Function swapFn = pool.getSwapFn(recipient, trade.isOrdered(), trade.getAmountIn(), bytes);

                // Add this encoded call to the lists
                targetList.add(sender);
                payloadList.add(new DynamicBytes(Numeric.hexStringToByteArray(encoder.encodeFunction(swapFn))));
                continue;
            }

            throw new IllegalStateException(String.format("Pool: %s is of an unknown or unsupported type", trade.getPool().getClass().getName()));
        }

        // Convert the running lists to DynamicArray Web3J type
        DynamicArray<Address> targets = new DynamicArray<>(Address.class, targetList);
        DynamicArray<DynamicBytes> payloads = new DynamicArray<>(DynamicBytes.class, payloadList);

        // Encode to byte[]
        byte[] bytes = Numeric.hexStringToByteArray(encoder.encodeParameters(List.of(targets, payloads)));

        // Encode the last swap call
        Trade lastTrade = trades.getLast();
        Function lastSwapFn = trades.getLast().getPool().getSwapFn(JAM_BOT_V3_ADDRESS, lastTrade.isOrdered(), lastTrade.getAmountIn(), lastTrade.getAmountOut(), bytes);

        callData = Numeric.hexStringToByteArray(encoder.encodeFunction(lastSwapFn));
    }

    public void simulate() throws GasEstimationFailureException {
        Function JAM_EXECUTE_TRADE_FN = getJamExecuteTradeFn(
                trades.get(0).getStart().getAddress(),
                trades.get(trades.size() - 1).getPool().getAddress(),
                callData);
        Transaction transaction = Transaction.createEthCallTransaction(DEPLOYER_ADDRESS, JAM_BOT_V3_ADDRESS.toString(), FunctionEncoder.encode(JAM_EXECUTE_TRADE_FN));
        EthEstimateGas response = null;
        try {
            response = SpringContext.getBean(Web3j.class).ethEstimateGas(transaction).send();
            if (!ObjectUtils.isEmpty(response.getError())) {
                throw GasEstimationFailureException.of();
            }
            // Usually the real amount of gas used is slightly less than estimated
            gasUsed = response.getAmountUsed().multiply(BigInteger.valueOf(95)).divide(BigInteger.valueOf(100));
        } catch (IOException exception) {
            throw GasEstimationFailureException.of(exception);
        }

        netProfitAfterBaseFee = profitBeforeGasInEther.toBigInteger().subtract(gasUsed.multiply(baseFeePerGas));
//        BigInteger lowerBoundPriority = ONE_GWEI;
//        BigInteger higherBoundPriority = ONE_GWEI.multiply(BigInteger.TEN);
        BigInteger numberOfWeiPerGasUnitFromProfit = netProfitAfterBaseFee.divide(gasUsed);
        BigInteger validatorSharePerGas = numberOfWeiPerGasUnitFromProfit.multiply(BigInteger.valueOf(99)).divide(BigInteger.valueOf(100));
//        if (validatorSharePerGas.compareTo(lowerBoundPriority) <= 0) {
//            maxPriorityFeePerGas = lowerBoundPriority;
//        } else if (validatorSharePerGas.compareTo(higherBoundPriority) > 0) {
//            maxPriorityFeePerGas = higherBoundPriority;
//        } else {
//            maxPriorityFeePerGas = validatorSharePerGas;
//        }
        maxPriorityFeePerGas = baseFeePerGas.add(validatorSharePerGas);
        netProfit = netProfitAfterBaseFee.subtract(gasUsed.multiply(validatorSharePerGas));
    }

    public void buildSignedTransaction() {
        String startToken = trades.getFirst().getStart().getAddress();
        String target = trades.getLast().getPool().getAddress();

        String data = FunctionEncoder.encode(getJamExecuteTradeFn(startToken, target, callData));

        BigInteger nonce;
        try {
            nonce = SpringContext.getBean(Web3j.class).ethGetTransactionCount(DEPLOYER_ADDRESS, DefaultBlockParameter.valueOf("latest")).send().getTransactionCount();
        } catch (IOException exception) {
            throw RPCCallFailureException.of("Failure to get nonce (transaction count) from deployer address", exception);
        }
        RawTransaction transaction = RawTransaction.createTransaction(
                1L,
                nonce,
                gasUsed.multiply(BigInteger.valueOf(150)).divide(BigInteger.valueOf(100)),
                JAM_BOT_V3_ADDRESS.toString(),
                ZERO,
                data,
                maxPriorityFeePerGas,
                maxPriorityFeePerGas
        );
        byte[] signedTransaction = TransactionEncoder.signMessage(transaction, SpringContext.getBean(Wallet.class).getAccount(0).getCredentials());
        this.signedTransaction = Numeric.toHexString(signedTransaction);
    }

    @Override
    public int compareTo(@NotNull Path o) {
        Preconditions.checkState(profitBeforeGasInEther != null, "Cannot compare paths because the profitBeforeGasInEther field is null");
        return (!ObjectUtils.isEmpty(netProfitAfterBaseFee) && !ObjectUtils.isEmpty(o.netProfitAfterBaseFee)) ? netProfitAfterBaseFee.compareTo(o.netProfitAfterBaseFee) : profitBeforeGasInEther.compareTo(o.profitBeforeGasInEther);
    }
}
