package dev.rabbit.pojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.exception.EthPriceRequestFailureException;
import dev.rabbit.exception.GasEstimationFailureException;
import dev.rabbit.exception.ZeroAmountOutException;
import dev.rabbit.record.Exchange;
import dev.rabbit.record.Node;
import dev.rabbit.utils.ValueConverter;
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
import static dev.rabbit.utils.Constants.ONE_GWEI;
import static dev.rabbit.utils.Constants.R1;
import static dev.rabbit.utils.Constants.R2;
import static dev.rabbit.utils.Constants.getERC20TransferFn;
import static dev.rabbit.utils.Constants.getJamExecuteTradeFn;
import static dev.rabbit.utils.Constants.getV2SwapFn;

@Slf4j
@Getter
public class Path implements Comparable<Path> {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final LinkedList<Trade> trades = new LinkedList<>();

    private BigDecimal virtualInputReserve;
    private BigDecimal virtualOutputReserve;
    private BigDecimal optimalTradingVolume;
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
    public void addTrade(Node start, Node end, Exchange pool, boolean ordered) {
        trades.add(Trade.builder()
                .start(Token.of(start))
                .end(Token.of(end))
                .pool(Pool.of(pool))
                .ordered(ordered)
                .build());
    }

    public void addTrade(Trade trade) {
        trades.add(Trade.builder()
                .start(Token.of(trade.getStart()))
                .end(Token.of(trade.getEnd()))
                .pool(Pool.of(trade.getPool()))
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
            BigInteger reserve0 = trade.getPool().getReserves().reserve0();
            BigInteger reserve1 = trade.getPool().getReserves().reserve1();

            if (trade.isOrdered()) {
                leftOperandNumerator = leftOperandNumerator.multiply(reserve1);
                leftOperandDenominator = leftOperandDenominator.multiply(reserve0);
            } else {
                leftOperandNumerator = leftOperandNumerator.multiply(reserve0);
                leftOperandDenominator = leftOperandDenominator.multiply(reserve1);
            }

            multipliedInputFees = multipliedInputFees.multiply(R1);
            multipliedOutputFees = multipliedOutputFees.multiply(R2);
        }

        BigDecimal rightOperandDenominator = multipliedInputFees.multiply(multipliedOutputFees);

        BigDecimal leftOperand = new BigDecimal(leftOperandNumerator).divide(new BigDecimal(leftOperandDenominator), 10, RoundingMode.HALF_DOWN);
        BigDecimal rightOperand = rightOperandNumerator.divide(rightOperandDenominator, 10, RoundingMode.HALF_DOWN);
        return leftOperand.compareTo(rightOperand) > 0;
    }

    // Math based on research paper -> https://arxiv.org/pdf/2105.02784.pdf
    public void calculateEquivalentLiquidity() {
        BigInteger[] orderedReserves = this.trades.get(0).getOrderedReserves();
        BigDecimal inputReserve = new BigDecimal(orderedReserves[0]);
        BigDecimal outputReserve = new BigDecimal(orderedReserves[1]);

        for (int i = 1; i < this.trades.size() - 1; i++) {
            orderedReserves = this.trades.get(i).getOrderedReserves();
            BigDecimal ithInputReserve = new BigDecimal(orderedReserves[0]);
            BigDecimal ithOutputReserve = new BigDecimal(orderedReserves[1]);

            BigDecimal iRNumerator = inputReserve.multiply(ithInputReserve);
            BigDecimal oRNumerator = R1.multiply(R2).multiply(outputReserve).multiply(ithOutputReserve);
            BigDecimal rDenominator = ithInputReserve.add(R1.multiply(R2).multiply(outputReserve));

            inputReserve = iRNumerator.divide(rDenominator, 30, RoundingMode.HALF_DOWN);
            outputReserve = oRNumerator.divide(rDenominator, 30, RoundingMode.HALF_DOWN);
        }

        this.virtualInputReserve = inputReserve;
        this.virtualOutputReserve = outputReserve;
    }

    // Math based on research paper -> https://arxiv.org/pdf/2105.02784.pdf
    public void calculateOptimalTradingVolume() {
        BigDecimal[] lastSwapOrderedReserves = getLastSwapOrderedReserves();
        BigDecimal lastSwapInputReserve = lastSwapOrderedReserves[0];
        BigDecimal lastSwapOutputReserve = lastSwapOrderedReserves[1];

        Supplier<BigDecimal> getAOrAPrimeDenominator = () -> lastSwapInputReserve.add(R1.multiply(R2).multiply(virtualOutputReserve));

        Supplier<BigDecimal> calculateA = () -> {
            BigDecimal aNumerator = virtualInputReserve.multiply(lastSwapInputReserve);
            return aNumerator.divide(getAOrAPrimeDenominator.get(), 30, RoundingMode.HALF_DOWN);
        };

        Supplier<BigDecimal> calculateAPrime = () -> {
            BigDecimal aPrimeNumerator = R1.multiply(R2).multiply(lastSwapOutputReserve).multiply(virtualOutputReserve);
            return aPrimeNumerator.divide(getAOrAPrimeDenominator.get(), 30, RoundingMode.HALF_DOWN);
        };

        BigDecimal a = calculateA.get();
        BigDecimal aPrime = calculateAPrime.get();
        BigDecimal numerator = R1.multiply(R2).multiply(aPrime).multiply(a).sqrt(MathContext.DECIMAL128).subtract(a);
        this.optimalTradingVolume = numerator.divide(R1, MathContext.DECIMAL128);
    }

    public void calculateRevenue(ValueConverter valueConverter) throws ZeroAmountOutException, IOException, EthPriceRequestFailureException {
        tradeAmounts = new BigInteger[this.trades.size() + 1];
        tradeAmounts[0] = this.optimalTradingVolume.toBigInteger();
        for (int i = 0; i < trades.size(); i++) {
            BigInteger[] orderedReserves = trades.get(i).getOrderedReserves();
            tradeAmounts[i + 1] = getAmountOut(tradeAmounts[i], orderedReserves[0], orderedReserves[1]);
        }
        this.profitBeforeGas = tradeAmounts[this.trades.size()].subtract(tradeAmounts[0]);
        this.profitBeforeGasInEther = valueConverter.deriveEtherValue(valueConverter.makeRequest(this.trades.get(0).getStart()), this.profitBeforeGas);
    }

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
                    ? getV2SwapFn(BigInteger.ZERO, this.tradeAmounts[i], this.trades.get(i).getPool().getAddress(), new byte[0])
                    : getV2SwapFn(this.tradeAmounts[i], BigInteger.ZERO, this.trades.get(i).getPool().getAddress(), new byte[0]);
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
                ? getV2SwapFn(BigInteger.ZERO, this.tradeAmounts[this.tradeAmounts.length - 1], JAM_BOT_ADDRESS, callData)
                : getV2SwapFn(this.tradeAmounts[this.tradeAmounts.length - 1], BigInteger.ZERO, JAM_BOT_ADDRESS, callData);

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
        this.netProfit = BigInteger.ZERO;
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
                BigInteger.ZERO,
                data,
                this.maxPriorityFeePerGas,
//                this.maxBaseFeeInFutureBlock.add(this.maxPriorityFeePerGas)
                this.maxPriorityFeePerGas
        );
        byte[] signedTransaction = TransactionEncoder.signMessage(transaction, wallet.getAccount(0).getCredentials());
        this.signedTransaction = Numeric.toHexString(signedTransaction);
    }

    private BigInteger getAmountOut(BigInteger amountIn, BigInteger reserveIn, BigInteger reserveOut) throws ZeroAmountOutException {
        checkInputs(amountIn, reserveIn, reserveOut);
        BigInteger amountInWithFee = amountIn.multiply(new BigInteger("997"));
        BigInteger numerator = amountInWithFee.multiply(reserveOut);
        BigInteger denominator = reserveIn.multiply(new BigInteger("1000")).add(amountInWithFee);
        return numerator.divide(denominator);
    }

    private BigDecimal[] getLastSwapOrderedReserves() {
        Trade lastTrade = this.trades.get(this.trades.size() - 1);
        BigDecimal lastTradeReserve0 = new BigDecimal(lastTrade.getPool().getReserves().reserve0());
        BigDecimal lastTradeReserve1 = new BigDecimal(lastTrade.getPool().getReserves().reserve1());
        BigDecimal lastSwapInputReserve = lastTrade.isOrdered() ? lastTradeReserve0 : lastTradeReserve1;
        BigDecimal lastSwapOutputReserve = lastTrade.isOrdered() ? lastTradeReserve1 : lastTradeReserve0;
        return new BigDecimal[]{lastSwapInputReserve, lastSwapOutputReserve};
    }

    private void checkInputs(BigInteger amountIn, BigInteger reserveIn, BigInteger reserveOut) throws ZeroAmountOutException {
        if (ObjectUtils.isEmpty(amountIn) ||
            ObjectUtils.isEmpty(reserveIn) ||
            ObjectUtils.isEmpty(reserveOut) ||
            amountIn.compareTo(BigInteger.ZERO) <= 0 ||
            reserveIn.compareTo(BigInteger.ZERO) <= 0 ||
            reserveOut.compareTo(BigInteger.ZERO) <= 0) {
            throw new ZeroAmountOutException();
        }
    }

    @Override
    public int compareTo(@NotNull Path o) {
        return (!ObjectUtils.isEmpty(this.netProfitAfterBaseFee) && !ObjectUtils.isEmpty(o.netProfitAfterBaseFee)) ? this.netProfitAfterBaseFee.compareTo(o.netProfitAfterBaseFee) : this.profitBeforeGasInEther.compareTo(o.profitBeforeGasInEther);
    }
}
