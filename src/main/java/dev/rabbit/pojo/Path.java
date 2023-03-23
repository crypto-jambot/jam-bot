package dev.rabbit.pojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.exception.GasEstimationFailureException;
import dev.rabbit.exception.ZeroAmountOutException;
import dev.rabbit.record.Exchange;
import dev.rabbit.record.Node;
import dev.rabbit.utils.ValueConverter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.ObjectUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static dev.rabbit.utils.Constants.JAM_BOT_ADDRESS;
import static dev.rabbit.utils.Constants.NULL_ADDRESS;
import static dev.rabbit.utils.Constants.R1;
import static dev.rabbit.utils.Constants.R2;
import static dev.rabbit.utils.Constants.getJamExecuteTradeFn;

@Slf4j
@Getter
public class Path implements Comparable<Path> {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final LinkedList<Trade> trades = new LinkedList<>();

    private BigDecimal virtualInputReserve;
    private BigDecimal virtualOutputReserve;
    private BigDecimal optimalTradingVolume;
    private BigInteger profitBeforeGas;
    private BigDecimal profitBeforeGasInEther;
    private BigInteger gasUsed;
    private BigInteger netProfit;
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

    public void calculateRevenue(ValueConverter valueConverter) throws ZeroAmountOutException, IOException {
        BigInteger[] amounts = new BigInteger[this.trades.size() + 1];
        amounts[0] = this.optimalTradingVolume.toBigInteger();
        for (int i = 0; i < trades.size(); i++) {
            BigInteger[] orderedReserves = trades.get(i).getOrderedReserves();
            amounts[i + 1] = getAmountOut(amounts[i], orderedReserves[0], orderedReserves[1]);
        }
        this.profitBeforeGas = amounts[this.trades.size()].subtract(amounts[0]);
        this.profitBeforeGasInEther = valueConverter.deriveEtherValue(valueConverter.makeRequest(this.trades.get(0).getStart()), this.profitBeforeGas);
    }

    public void simulate(Web3j web3j, BigInteger gasPrice) throws IOException, GasEstimationFailureException {
        Function JAM_EXECUTE_TRADE_FN = getJamExecuteTradeFn(
                this.trades.stream().map(trade -> trade.getStart().getAddress()).collect(Collectors.toList()),
                this.trades.stream().map(trade -> trade.getPool().getAddress()).collect(Collectors.toList()),
                new BigInteger(String.valueOf(this.optimalTradingVolume.setScale(0, RoundingMode.HALF_DOWN))));
        Transaction transaction = Transaction.createEthCallTransaction(NULL_ADDRESS, JAM_BOT_ADDRESS, FunctionEncoder.encode(JAM_EXECUTE_TRADE_FN));
        EthEstimateGas response = web3j.ethEstimateGas(transaction).send();
        if (!ObjectUtils.isEmpty(response.getError())) {
            throw GasEstimationFailureException.of();
        }
        this.gasUsed = response.getAmountUsed();
        this.netProfit = new BigInteger(String.valueOf(this.profitBeforeGasInEther.setScale(0, RoundingMode.HALF_DOWN))).subtract(this.gasUsed.multiply(gasPrice));
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
        return (!ObjectUtils.isEmpty(this.netProfit) && !ObjectUtils.isEmpty(o.netProfit)) ? this.netProfit.compareTo(o.netProfit) : this.profitBeforeGasInEther.compareTo(o.profitBeforeGasInEther);
    }
}
