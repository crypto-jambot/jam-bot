package dev.rabbit.pool;

import com.google.common.base.Preconditions;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import static dev.rabbit.utils.Constants.Q96;

public abstract class State {
    final static String INITIALIZE_ERROR_MESSAGE = "%s must not be null when initializing state.";
    final static String SETTER_ERROR_MESSAGE = "Cannot only set value, %s, if not initialized.";
    final static String RESET_ERROR_MESSAGE = "%s must not be null when resetting the ThreadLocal state.";
    public State() {
        useCache = false;
    }
    @Getter
    protected boolean useCache;
    public abstract void initialize();
    public abstract void clear();
    public abstract void reset();
}

class V3State extends State {
    public V3State() {
        super();
    }

    private BigInteger liquidity;
    private ThreadLocal<BigInteger> mutableLiquidity;
    private BigInteger sqrtPriceX96;
    private ThreadLocal<BigInteger> mutableSqrtPriceX96;
    private BigDecimal sqrtPrice;
    private ThreadLocal<BigDecimal> mutableSqrtPrice;
    private BigInteger tick;
    private ThreadLocal<BigInteger> mutableTick;

    @Override
    public void initialize() {
        if (useCache) return;
        Preconditions.checkState(liquidity != null, String.format(INITIALIZE_ERROR_MESSAGE, "liquidity"));
        Preconditions.checkState(sqrtPriceX96 != null, String.format(INITIALIZE_ERROR_MESSAGE, "sqrtPriceX96"));
        Preconditions.checkState(sqrtPrice != null, String.format(INITIALIZE_ERROR_MESSAGE, "sqrtPrice"));
        Preconditions.checkState(tick != null, String.format(INITIALIZE_ERROR_MESSAGE, "tick"));
        useCache = true;
        mutableLiquidity = ThreadLocal.withInitial(() -> liquidity);
        mutableSqrtPriceX96 = ThreadLocal.withInitial(() -> sqrtPriceX96);
        mutableSqrtPrice = ThreadLocal.withInitial(() -> sqrtPrice);
        mutableTick = ThreadLocal.withInitial(() -> tick);
    }

    @Override
    public void clear() {
        useCache = false;
        mutableLiquidity = null;
        mutableSqrtPriceX96 = null;
        mutableSqrtPrice = null;
        mutableTick = null;
    }

    @Override
    public void reset() {
        Preconditions.checkState(mutableLiquidity != null, String.format(RESET_ERROR_MESSAGE, "mutableLiquidity"));
        Preconditions.checkState(mutableSqrtPriceX96 != null, String.format(RESET_ERROR_MESSAGE, "mutableSqrtPriceX96"));
        Preconditions.checkState(mutableSqrtPrice != null, String.format(RESET_ERROR_MESSAGE, "mutableSqrtPrice"));
        Preconditions.checkState(mutableTick != null, String.format(RESET_ERROR_MESSAGE, "mutableTick"));
        mutableLiquidity.remove();
        mutableSqrtPriceX96.remove();
        mutableSqrtPrice.remove();
        mutableTick.remove();
    }

    BigInteger getLiquidity() {
        return useCache ? mutableLiquidity.get() : liquidity;
    }
    BigInteger getSqrtPriceX96() {
        return useCache ? mutableSqrtPriceX96.get() : sqrtPriceX96;
    }
    BigDecimal getSqrtPrice() {
        return useCache ? mutableSqrtPrice.get() : sqrtPrice;
    }
    BigInteger getTick() {
        return useCache ? mutableTick.get() : tick;
    }
    void setLiquidity(BigInteger liquidity) {
        if (useCache) {
            mutableLiquidity.set(liquidity);
            return;
        }
        Preconditions.checkState(this.liquidity == null, String.format(SETTER_ERROR_MESSAGE, "liquidity"));
        this.liquidity = liquidity;
    }
    void setSqrtPriceX96(BigInteger sqrtPriceX96) {
        if (useCache) {
            mutableSqrtPriceX96.set(sqrtPriceX96);
            mutableSqrtPrice.set(new BigDecimal(mutableSqrtPriceX96.get()).divide(new BigDecimal(Q96), 30, RoundingMode.HALF_DOWN));
            return;
        }
        Preconditions.checkState(this.sqrtPriceX96 == null, String.format(SETTER_ERROR_MESSAGE, "sqrtPriceX96"));
        this.sqrtPriceX96 = sqrtPriceX96;
        sqrtPrice = new BigDecimal(this.sqrtPriceX96).divide(new BigDecimal(Q96), 30, RoundingMode.HALF_DOWN);
    }
    void setTick(BigInteger tick) {
        if (useCache) {
            mutableTick.set(tick);
            return;
        }
        Preconditions.checkState(this.tick == null, String.format(SETTER_ERROR_MESSAGE, "tick"));
        this.tick = tick;
    }
}

class V2State extends State {
    public V2State() {
        super();
    }

    private BigInteger reserve0;
    private ThreadLocal<BigInteger> mutableReserve0;
    private BigInteger reserve1;
    private ThreadLocal<BigInteger> mutableReserve1;
    @Override
    public void initialize() {
        if (useCache) return;
        Preconditions.checkState(reserve0 != null, String.format(INITIALIZE_ERROR_MESSAGE, "reserve0"));
        Preconditions.checkState(reserve1 != null, String.format(INITIALIZE_ERROR_MESSAGE, "reserve1"));
        useCache = true;
        mutableReserve0 = ThreadLocal.withInitial(() -> reserve0);
        mutableReserve1 = ThreadLocal.withInitial(() -> reserve1);
    }

    @Override
    public void clear() {
        useCache = false;
        mutableReserve0 = null;
        mutableReserve1 = null;
    }

    @Override
    public void reset() {
        Preconditions.checkState(mutableReserve0 != null, String.format(RESET_ERROR_MESSAGE, "mutableReserve0"));
        Preconditions.checkState(mutableReserve1 != null, String.format(RESET_ERROR_MESSAGE, "mutableReserve1"));
        mutableReserve0.remove();
        mutableReserve1.remove();
    }

    BigInteger getReserve0() {
        return useCache ? mutableReserve0.get() : reserve0;
    }
    BigInteger getReserve1() {
        return useCache ? mutableReserve1.get() : reserve1;
    }
    void setReserve0(BigInteger reserve0) {
        if (useCache) {
            mutableReserve0.set(reserve0);
            return;
        }
        Preconditions.checkState(this.reserve0 == null, String.format(SETTER_ERROR_MESSAGE, "reserve0"));
        this.reserve0 = reserve0;
    }

    void setReserve1(BigInteger reserve1) {
        if (useCache) {
            mutableReserve1.set(reserve1);
            return;
        }
        Preconditions.checkState(this.reserve1 == null, String.format(SETTER_ERROR_MESSAGE, "reserve1"));
        this.reserve1 = reserve1;
    }
}
