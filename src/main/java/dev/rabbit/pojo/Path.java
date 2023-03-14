package dev.rabbit.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.record.Exchange;
import dev.rabbit.record.Node;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.function.Supplier;

import static dev.rabbit.utils.Constants.R1;
import static dev.rabbit.utils.Constants.R2;

@JsonPropertyOrder({"profitBeforeGas"})
@Slf4j
public class Path implements Comparable<Path> {
    private static final ObjectMapper mapper = new ObjectMapper();
    @Getter
    @JsonProperty
    private final LinkedList<Trade> trades = new LinkedList<>();

    @JsonProperty
    private BigDecimal virtualInputReserve;
    @JsonProperty
    private BigDecimal virtualOutputReserve;
    @JsonProperty
    private BigDecimal optimalTradingVolume;
    @JsonProperty
    @Getter
    private BigDecimal profitBeforeGas;
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

    @JsonIgnore
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

    public void calculateRevenue() {
        BigDecimal tradeNAmountOut = getAmountOut(this.optimalTradingVolume, virtualInputReserve, virtualOutputReserve);
        BigDecimal[] lastSwapOrderedReserves = getLastSwapOrderedReserves();
        BigDecimal lastSwapInputReserve = lastSwapOrderedReserves[0];
        BigDecimal lastSwapOutputReserve = lastSwapOrderedReserves[1];
        BigDecimal revenue = getAmountOut(tradeNAmountOut, lastSwapInputReserve, lastSwapOutputReserve);
        this.profitBeforeGas = revenue.subtract(optimalTradingVolume);
    }

    private BigDecimal getAmountOut(BigDecimal amountIn, BigDecimal reserveIn, BigDecimal reserveOut) {
        if (amountIn == null || !(amountIn.compareTo(BigDecimal.ZERO) > 0)) {
            log.error("Amount In is null or zero");
            throw new RuntimeException();
        }
        if (reserveIn == null || !(reserveIn.compareTo(BigDecimal.ZERO) > 0)) {
            log.error("ReserveIn is null or zero");
            throw new RuntimeException();
        }
        if (reserveOut == null || !(reserveOut.compareTo(BigDecimal.ZERO) > 0)) {
            log.error("ReserveOut is null or zero");
            throw new RuntimeException();
        }
        BigDecimal amountInWithFee = amountIn.multiply(new BigDecimal("997"));
        BigDecimal numerator = amountInWithFee.multiply(reserveOut);
        BigDecimal denominator = reserveIn.multiply(new BigDecimal("1000")).add(amountInWithFee);
        return numerator.divide(denominator, MathContext.DECIMAL128);
    }

    private BigDecimal[] getLastSwapOrderedReserves() {
        Trade lastTrade = this.trades.get(this.trades.size() - 1);
        BigDecimal lastTradeReserve0 = new BigDecimal(lastTrade.getPool().getReserves().reserve0());
        BigDecimal lastTradeReserve1 = new BigDecimal(lastTrade.getPool().getReserves().reserve1());
        BigDecimal lastSwapInputReserve = lastTrade.isOrdered() ? lastTradeReserve0 : lastTradeReserve1;
        BigDecimal lastSwapOutputReserve = lastTrade.isOrdered() ? lastTradeReserve1 : lastTradeReserve0;
        return new BigDecimal[]{lastSwapInputReserve, lastSwapOutputReserve};
    }

    @Override
    public String toString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int compareTo(@NotNull Path o) {
        return this.profitBeforeGas.compareTo(o.profitBeforeGas);
    }
}
