package dev.rabbit.pool;

import dev.rabbit.pojo.Memento;
import dev.rabbit.pojo.Revertable;
import dev.rabbit.record.Tree;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthCall;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class Pool<T extends PoolState> {
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
    public abstract void assignData(List<Response<?>> responses);
    public abstract BigInteger getReserve0();
    public abstract BigInteger getReserve1();
    public abstract BigInteger getAmountOutAssumingNoTickCrossings(BigInteger amountIn, boolean ordered);
    public abstract BigInteger getAmountInAssumingNoTickCrossings(BigInteger amountOut, boolean ordered);
    public BigInteger getFullInputAmountOrToFirstTick(BigInteger maxAmount, boolean ordered) {
        return maxAmount;
    }
    Memento createMemento() throws IllegalAccessException {
        Map<Field, Object> fieldValues = new HashMap<>();
        Class<?> poolClass = getClass();
        for (Field field : poolClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Revertable.class)) {
                field.setAccessible(true);
                fieldValues.put(field, field.get(this));
            }
        }
        return new Memento(fieldValues);
    }
    void restoreMemento(Memento memento) throws IllegalAccessException {
        Map<Field, Object> fieldValues = memento.fieldValues();
        for (Map.Entry<Field, Object> entry : fieldValues.entrySet()) {
            Field field = entry.getKey();
            field.setAccessible(true);
            field.set(this, entry.getValue());
        }
    }
}
