package dev.rabbit.pojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Tuple;
import dev.rabbit.exception.GasEstimationFailureException;
import dev.rabbit.exception.ZeroAmountException;
import dev.rabbit.pool.Pool;
import dev.rabbit.pool.PoolState;
import dev.rabbit.record.Tree;
import dev.rabbit.utils.Wallet;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import static dev.rabbit.utils.Constants.DEPLOYER_ADDRESS;
import static dev.rabbit.utils.Constants.JAM_BOT_ADDRESS;
import static dev.rabbit.utils.Constants.R2;
import static dev.rabbit.utils.Constants.feeToPercent;
import static dev.rabbit.utils.Constants.getERC20TransferFn;
import static dev.rabbit.utils.Constants.getJamExecuteTradeFn;
import static dev.rabbit.utils.Constants.getV2SwapFn;
import static java.math.BigInteger.ZERO;

@Slf4j
@Getter
public class Path implements Comparable<Path> {
    private final LinkedList<Trade> trades = new LinkedList<>();
    private BigInteger optimalTradingVolume = ZERO;
    private BigInteger[] tradeAmounts;
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

    // Math based on research paper -> https://arxiv.org/pdf/2105.02784.pdf
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

        BigDecimal leftOperand = new BigDecimal(leftOperandNumerator).divide(new BigDecimal(leftOperandDenominator), 10, RoundingMode.HALF_DOWN);
        BigDecimal rightOperand = rightOperandNumerator.divide(rightOperandDenominator, 10, RoundingMode.HALF_DOWN);
        return leftOperand.compareTo(rightOperand) > 0;
    }

    // Math based on research paper -> https://arxiv.org/pdf/2105.02784.pdf
    // Derive the virtual reserves with the ith fee
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

                inputReserve = iRNumerator.divide(rDenominator, 30, RoundingMode.HALF_DOWN);
                outputReserve = oRNumerator.divide(rDenominator, 30, RoundingMode.HALF_DOWN);
            }
            BigDecimal numerator = feeToPercent(fee).multiply(R2).multiply(outputReserve).multiply(inputReserve).sqrt(MathContext.DECIMAL128).subtract(inputReserve);
            return numerator.divide(feeToPercent(fee), MathContext.DECIMAL128);
        };

        // Determine initial optimal trading amount
        // Input amount is subject to change if ticks are encountered
        BigInteger inputAmount = getOptimalAmountFn.get().toBigInteger();
        // Sometimes the optimal amount is so small that flooring via toBigInteger() results in 0
        // This path does not contain extractable value
        if (inputAmount.equals(ZERO)) throw new ZeroAmountException();

        /* A tick crossing changes the state of a pool. This throws off the optimal amount equation.
            As long as our calculated optimal amount encounters ticks during the swap, we must:
                1. Determine the earliest point in the trade path where a tick is encountered
                2. Perform a partial swap up to the tick, and recalculate the state of all the pool reserves
                3. Keep track of the input amount from the partial swap
                3. From the new state, recalculate the optimal amount
                5. Repeat until we complete the full swap. Add up all the input amounts from partial swaps
                6. Return the final optimal amount of the trade path
         */
        while (true) {
            BigInteger inputAmountToEarliestTick = null;

            // Populate input and outputs from each trade assuming no tick crossings
            // This computes the initial trade amounts through the path.
            populateInputAndOutputTradeAmounts(inputAmount);

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
                    inputAmount = ithTradeFullInputAmountOrToFirstTick;
                    continue;
                }

                // If we encounter a tick, and it is not the first trade, then we must recalculate the initial input amount into the path to swap up to this tick
                BigInteger inputAmountToEarliestTickTemp = ithTradeFullInputAmountOrToFirstTick;
                // Recalculate trade inputs to derive the initial input for the path to swap to the tick crossing
                for (int j = i - 1; j >= 0; j--) {
                    inputAmountToEarliestTickTemp = trades.get(j).getAmountInAssumingNoTickCrossings(inputAmountToEarliestTickTemp);
                }

                // Assign the new input amount if it is less than what is currently stored.
                inputAmountToEarliestTick = inputAmountToEarliestTick == null
                        ? inputAmountToEarliestTickTemp
                        : inputAmountToEarliestTick.min(inputAmountToEarliestTickTemp);
            }

            // If inputAmountToEarliestTick is null, that means no ticks were encountered in the path.
            if (inputAmountToEarliestTick == null) {
                optimalTradingVolume = inputAmount;
                // Clear state cache on all pools
                trades.forEach(trade -> trade.getPool().getState().clear());
                return;
            }

            optimalTradingVolume = optimalTradingVolume.add(inputAmountToEarliestTick);

            // Initialize state cache if not already initialized
            trades.forEach(trade -> {
                PoolState state = trade.getPool().getState();
                if (!state.isUseCache()) state.initialize();
            });

            // Todo
            // Perform a partial dry run of a swap to the earliest tick
        }
    }

//    public void calculateRevenue(ValueConverter valueConverter) throws ZeroAmountOutException, IOException, EthPriceRequestFailureException {
//        tradeAmounts = new BigInteger[this.trades.size() + 1];
//        tradeAmounts[0] = this.optimalTradingVolume.toBigInteger();
//        for (int i = 0; i < trades.size(); i++) {
//            BigInteger[] orderedReserves = trades.get(i).getOrderedReserves();
//            tradeAmounts[i + 1] = getAmountOut(tradeAmounts[i], orderedReserves[0], orderedReserves[1]);
//        }
//        this.profitBeforeGas = tradeAmounts[this.trades.size()].subtract(tradeAmounts[0]);
//        this.profitBeforeGasInEther = valueConverter.deriveEtherValue(valueConverter.makeRequest(this.trades.get(0).getStart()), this.profitBeforeGas);
//    }

    public void encodeCallData() {
        DefaultFunctionEncoder encoder = new DefaultFunctionEncoder();

        Address[] targetArray = new Address[this.trades.size()];
        DynamicBytes[] payloadArray = new DynamicBytes[this.trades.size()];

        // Encode the first ERC20 Transfer
        targetArray[0] = new Address(this.trades.get(0).getStart().getAddress());
        payloadArray[0] = new DynamicBytes(Numeric.hexStringToByteArray(encoder.encodeFunction(getERC20TransferFn(this.trades.get(0).getPool().getAddress(), this.tradeAmounts[0]))));

        // Encode all the swap calls except the last one
        for (int i = 1; i < this.trades.size(); i++) {
            targetArray[i] = new Address(this.trades.get(i - 1).getPool().getAddress());
            Function v2SwapFn = this.trades.get(i - 1).isOrdered()
                    ? getV2SwapFn(ZERO, this.tradeAmounts[i], this.trades.get(i).getPool().getAddress(), new byte[0])
                    : getV2SwapFn(this.tradeAmounts[i], ZERO, this.trades.get(i).getPool().getAddress(), new byte[0]);
            payloadArray[i] = new DynamicBytes(Numeric.hexStringToByteArray(encoder.encodeFunction(v2SwapFn)));
        }

        // Convert arrays to DynamicArray Web3J type
        DynamicArray<Address> targets = new DynamicArray<>(Address.class, Arrays.asList(targetArray));
        DynamicArray<DynamicBytes> payloads = new DynamicArray<>(DynamicBytes.class, Arrays.asList(payloadArray));

        String callDataString = encoder.encodeParameters(List.of(targets, payloads));
        byte[] callData = Numeric.hexStringToByteArray(callDataString);

        // Encode the last swap call with calldata
        Trade lastTrade = this.trades.getLast();
        Function v2SwapFnWithCallData = lastTrade.isOrdered()
                ? getV2SwapFn(ZERO, this.tradeAmounts[this.tradeAmounts.length - 1], JAM_BOT_ADDRESS, callData)
                : getV2SwapFn(this.tradeAmounts[this.tradeAmounts.length - 1], ZERO, JAM_BOT_ADDRESS, callData);

        this.callData = Numeric.hexStringToByteArray(encoder.encodeFunction(v2SwapFnWithCallData));
    }

    public void simulate(Web3j web3j) throws IOException, GasEstimationFailureException {
        Function JAM_EXECUTE_TRADE_FN = getJamExecuteTradeFn(
                this.trades.get(0).getStart().getAddress(),
                this.trades.get(this.trades.size() - 1).getPool().getAddress(),
                this.callData);
        Transaction transaction = Transaction.createEthCallTransaction(DEPLOYER_ADDRESS, JAM_BOT_ADDRESS, FunctionEncoder.encode(JAM_EXECUTE_TRADE_FN));
        EthEstimateGas response = web3j.ethEstimateGas(transaction).send();
        if (!ObjectUtils.isEmpty(response.getError())) {
            throw GasEstimationFailureException.of();
        }
        this.gasUsed = response.getAmountUsed().multiply(BigInteger.valueOf(95)).divide(BigInteger.valueOf(100));
        this.netProfitAfterBaseFee = new BigInteger(String.valueOf(this.profitBeforeGasInEther.setScale(0, RoundingMode.HALF_DOWN))).subtract(this.gasUsed.multiply(this.baseFeePerGas));
//        BigInteger lowerBoundPriority = ONE_GWEI;
//        BigInteger higherBoundPriority = ONE_GWEI.multiply(BigInteger.TEN);
//        BigInteger numberOfWeiPerGasUnitFromProfit = netProfitAfterBaseFee.divide(this.gasUsed);
//        BigInteger validatorSharePerGas = numberOfWeiPerGasUnitFromProfit.multiply(BigInteger.valueOf(99)).divide(BigInteger.valueOf(100));
//        if (validatorSharePerGas.compareTo(lowerBoundPriority) <= 0) {
//            this.maxPriorityFeePerGas = lowerBoundPriority;
//        } else if (validatorSharePerGas.compareTo(higherBoundPriority) > 0) {
//            this.maxPriorityFeePerGas = higherBoundPriority;
//        } else {
//            this.maxPriorityFeePerGas = validatorSharePerGas;
//        }
        this.maxPriorityFeePerGas = new BigInteger(String.valueOf(this.profitBeforeGasInEther.setScale(0, RoundingMode.HALF_DOWN))).divide(this.gasUsed);
//        this.netProfit = netProfitAfterBaseFee.subtract(this.gasUsed.multiply(this.maxPriorityFeePerGas));
        this.netProfit = ZERO;
    }

    public void buildSignedTransaction(Web3j web3j, Wallet wallet) throws IOException {
        String startToken = this.trades.getFirst().getStart().getAddress();
        String target = this.trades.getLast().getPool().getAddress();

        String data = FunctionEncoder.encode(getJamExecuteTradeFn(startToken, target, this.callData));
        RawTransaction transaction = RawTransaction.createTransaction(
                1L,
                web3j.ethGetTransactionCount(DEPLOYER_ADDRESS, DefaultBlockParameter.valueOf("latest")).send().getTransactionCount(),
                BigDecimal.valueOf(1.5).multiply(new BigDecimal(String.valueOf(this.gasUsed))).toBigInteger(),
                JAM_BOT_ADDRESS,
                ZERO,
                data,
                this.maxPriorityFeePerGas,
//                this.maxBaseFeeInFutureBlock.add(this.maxPriorityFeePerGas)
                this.maxPriorityFeePerGas
        );
        byte[] signedTransaction = TransactionEncoder.signMessage(transaction, wallet.getAccount(0).getCredentials());
        this.signedTransaction = Numeric.toHexString(signedTransaction);
    }

//    private BigInteger getAmountOut(BigInteger amountIn, BigInteger reserveIn, BigInteger reserveOut) throws ZeroAmountOutException {
//        checkInputs(amountIn, reserveIn, reserveOut);
//        BigInteger amountInWithFee = amountIn.multiply(new BigInteger("997"));
//        BigInteger numerator = amountInWithFee.multiply(reserveOut);
//        BigInteger denominator = reserveIn.multiply(new BigInteger("1000")).add(amountInWithFee);
//        return numerator.divide(denominator);
//    }

//    private BigDecimal[] getLastSwapOrderedReserves() {
//        return (BigDecimal[]) Arrays.stream(trades.getLast().getOrderedReserves()).map(BigDecimal::new).toArray();
//        Trade lastTrade = this.trades.get(this.trades.size() - 1);
//        BigDecimal lastTradeReserve0 = new BigDecimal(lastTrade.getPool().getReserves().reserve0());
//        BigDecimal lastTradeReserve1 = new BigDecimal(lastTrade.getPool().getReserves().reserve1());
//        BigDecimal lastSwapInputReserve = lastTrade.isOrdered() ? lastTradeReserve0 : lastTradeReserve1;
//        BigDecimal lastSwapOutputReserve = lastTrade.isOrdered() ? lastTradeReserve1 : lastTradeReserve0;
//        return new BigDecimal[]{lastSwapInputReserve, lastSwapOutputReserve};
//    }
    public void populateInputAndOutputTradeAmounts(BigInteger initialAmountIn) {
        trades.get(0).setAmountIn(initialAmountIn);
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            trade.computeAndSetAmountOutAssumingNoTickCrossings();
            if (i != trades.size() - 1) trades.get(i + 1).setAmountIn(trade.getAmountOut());
        }
    }

    private void checkInputs(BigInteger amountIn, BigInteger reserveIn, BigInteger reserveOut) throws ZeroAmountException {
        if (ObjectUtils.isEmpty(amountIn) ||
            ObjectUtils.isEmpty(reserveIn) ||
            ObjectUtils.isEmpty(reserveOut) ||
            amountIn.compareTo(ZERO) <= 0 ||
            reserveIn.compareTo(ZERO) <= 0 ||
            reserveOut.compareTo(ZERO) <= 0) {
            throw new ZeroAmountException();
        }
    }

    @Override
    public int compareTo(@NotNull Path o) {
        return (!ObjectUtils.isEmpty(this.maxPriorityFeePerGas) && !ObjectUtils.isEmpty(o.maxPriorityFeePerGas)) ? this.maxPriorityFeePerGas.compareTo(o.maxPriorityFeePerGas) : this.profitBeforeGasInEther.compareTo(o.profitBeforeGasInEther);
    }
}
