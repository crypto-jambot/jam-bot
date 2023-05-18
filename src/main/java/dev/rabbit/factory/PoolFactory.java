package dev.rabbit.factory;

import dev.rabbit.pool.Pool;
import dev.rabbit.record.Tree;
import lombok.AllArgsConstructor;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Map;

@AllArgsConstructor
public class PoolFactory {
    private final Map<String, Class<? extends Pool>> poolTypes;
    private final Map<String, Pool> pools;

    public Pool createPool(Tree.Swap.Exchange exchange) {
        Class<? extends Pool> poolClass = poolTypes.get(exchange.name());
        if (poolClass == null) {
            throw new IllegalArgumentException("Unsupported protocol type: " + exchange.name());
        }

        if (!pools.containsKey(exchange.pool())) {
            try {
                Pool newPool = poolClass.getConstructor(new Class[] { Tree.Swap.Exchange.class }).newInstance(exchange);
                pools.put(exchange.pool(), newPool);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException("Failed to create pool", e);
            }
        }

        return pools.get(exchange.pool());
    }

    public void clearPools() {
        pools.clear();
    }

    public Collection<Pool> getAllPools() {
        return pools.values();
    }
}
