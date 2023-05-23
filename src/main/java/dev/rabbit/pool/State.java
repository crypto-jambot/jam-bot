package dev.rabbit.pool;

import com.google.common.base.Preconditions;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import static dev.rabbit.utils.Constants.Q96;

public abstract class State {
    public State() {
        useCache = false;
    }
    @Getter
    protected boolean useCache;
    public abstract void initialize();
    public abstract void clear();
}

class V3State extends State {
    public V3State() {
        super();
    }

    private BigInteger liquidity;
    private BigInteger mutableLiquidity;
    private BigInteger sqrtPriceX96;
    private BigInteger mutableSqrtPriceX96;
    private BigDecimal sqrtPrice;
    private BigDecimal mutableSqrtPrice;
    private BigInteger tick;
    private BigInteger mutableTick;

    @Override
    public void initialize() {
        useCache = true;
        mutableLiquidity = new BigInteger(liquidity.toString());
        mutableSqrtPriceX96 = new BigInteger(sqrtPriceX96.toString());
        mutableSqrtPrice = new BigDecimal(sqrtPrice.toString());
        mutableTick = new BigInteger(tick.toString());
    }

    @Override
    public void clear() {
        useCache = false;
        mutableLiquidity = null;
        mutableSqrtPriceX96 = null;
        mutableSqrtPrice = null;
        mutableTick = null;
    }

    BigInteger getLiquidity() {
        return useCache ? mutableLiquidity : liquidity;
    }
    BigInteger getSqrtPriceX96() {
        return useCache ? mutableSqrtPriceX96 : sqrtPriceX96;
    }
    BigDecimal getSqrtPrice() {
        return useCache ? mutableSqrtPrice : sqrtPrice;
    }
    BigInteger getTick() {
        return useCache ? mutableTick : tick;
    }
    void setLiquidity(BigInteger liquidity) {
        if (useCache) {
            mutableLiquidity = liquidity;
            return;
        }
        Preconditions.checkState(this.liquidity == null, "Cannot only set value if not initialized.");
        this.liquidity = liquidity;
    }
    void setSqrtPriceX96(BigInteger sqrtPriceX96) {
        if (useCache) {
            mutableSqrtPriceX96 = sqrtPriceX96;
            mutableSqrtPrice = new BigDecimal(mutableSqrtPriceX96).divide(new BigDecimal(Q96), 30, RoundingMode.HALF_DOWN);
            return;
        }
        Preconditions.checkState(this.sqrtPriceX96 == null, "Cannot only set value if not initialized.");
        this.sqrtPriceX96 = sqrtPriceX96;
        sqrtPrice = new BigDecimal(this.sqrtPriceX96).divide(new BigDecimal(Q96), 30, RoundingMode.HALF_DOWN);
    }
    void setTick(BigInteger tick) {
        if (useCache) {
            mutableTick = tick;
            return;
        }
        Preconditions.checkState(this.tick == null, "Cannot only set value if not initialized.");
        this.tick = tick;
    }
}

class V2State extends State {
    public V2State() {
        super();
    }

    private BigInteger reserve0;
    private BigInteger mutableReserve0;
    private BigInteger reserve1;
    private BigInteger mutableReserve1;
    @Override
    public void initialize() {
        useCache = true;
        mutableReserve0 = new BigInteger(reserve0.toString());
        mutableReserve1 = new BigInteger(reserve1.toString());
    }

    @Override
    public void clear() {
        useCache = false;
        mutableReserve0 = null;
        mutableReserve1 = null;
    }

    BigInteger getReserve0() {
        return useCache ? mutableReserve0 : reserve0;
    }
    BigInteger getReserve1() {
        return useCache ? mutableReserve1 : reserve1;
    }
    void setReserve0(BigInteger reserve0) {
        if (useCache) {
            mutableReserve0 = reserve0;
            return;
        }
        Preconditions.checkState(this.reserve0 == null, "Cannot only set value if not initialized.");
        this.reserve0 = reserve0;
    }

    void setReserve1(BigInteger reserve1) {
        if (useCache) {
            mutableReserve1 = reserve1;
            return;
        }
        Preconditions.checkState(this.reserve1 == null, "Cannot only set value if not initialized.");
        this.reserve1 = reserve1;
    }
}
