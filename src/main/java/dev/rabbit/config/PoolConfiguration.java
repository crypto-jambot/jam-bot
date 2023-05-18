package dev.rabbit.config;

import dev.rabbit.exception.PoolClassNotFoundException;
import dev.rabbit.factory.PoolFactory;
import dev.rabbit.pool.Pool;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "defi")
@Data
public class PoolConfiguration {
    private Map<String, Map<String, String>> protocols;

    @Bean
    public PoolFactory poolFactory() {
        Map<String, Class<? extends Pool>> poolTypes = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> protocol : protocols.entrySet()) {
            String protocolName = protocol.getKey();
            Map<String, String> protocolMap = protocol.getValue();
            String poolClassName = protocolMap.get("poolType");
            try {
                Class<? extends Pool> poolClass = Class.forName(poolClassName).asSubclass(Pool.class);
                poolTypes.put(protocolName, poolClass);
            } catch (ClassNotFoundException e) {
                throw PoolClassNotFoundException.of(poolClassName);
            }
        }
        return new PoolFactory(poolTypes, new HashMap<>());
    }
}
