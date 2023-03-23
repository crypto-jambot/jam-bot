package dev.rabbit.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.pojo.Token;
import lombok.AllArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import static dev.rabbit.utils.Constants.WETH_ADDRESS;

@Component
@AllArgsConstructor
public class ValueConverter {
    private final OkHttpClient okHttpClient;
    private final ObjectMapper mapper;

    private static final String URL = "https://min-api.cryptocompare.com/data/price?fsym=%s&tsyms=ETH";


    public BigDecimal deriveEtherValue(Double ethPrice, BigInteger profitBeforeGas) {
        return BigDecimal.valueOf(ethPrice).multiply(new BigDecimal(profitBeforeGas));
    }

    @Cacheable(value="token", key="#token")
    public Double makeRequest(Token token) throws IOException {
        if(token.getAddress().equalsIgnoreCase(WETH_ADDRESS)) return Double.valueOf("1.0");
        Request request = new Request.Builder()
                .url(String .format(URL, token.getSymbol()))
                .build();
        try(Response response = okHttpClient.newCall(request).execute()) {
            String result = response.body().string();
            Map<String, Double> responseMap =  mapper.readValue(result, Map.class);
            return responseMap.get("ETH");
        }
    }

    @CacheEvict(value = "token", allEntries = true)
    @Scheduled(fixedDelay = 300000)
    public void evictCache() {}

}
