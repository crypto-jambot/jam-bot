package dev.rabbit.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.exception.EthPriceRequestFailureException;
import dev.rabbit.pojo.Token;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import static dev.rabbit.utils.Constants.WETH_ADDRESS;

@Component
@AllArgsConstructor
@Slf4j
public class ValueConverter {
    private final OkHttpClient okHttpClient;
    private final ObjectMapper mapper;
    private static final String URL = "https://min-api.cryptocompare.com/data/price?fsym=%s&tsyms=ETH";

    public BigDecimal deriveEtherValue(Double ethPrice, BigInteger profitBeforeGas) {
        return BigDecimal.valueOf(ethPrice).multiply(new BigDecimal(profitBeforeGas));
    }

    @Cacheable(value="token", key="#token")
    public Double makeRequest(Token token) throws IOException, EthPriceRequestFailureException {
        if(token.getAddress().equalsIgnoreCase(WETH_ADDRESS)) return Double.valueOf("1.0");
        Request request = new Request.Builder()
                .url(String .format(URL, token.getSymbol()))
                .build();
        try(Response response = okHttpClient.newCall(request).execute()) {
            if (ObjectUtils.isEmpty(response) || ObjectUtils.isEmpty(response.body())) {
                throw EthPriceRequestFailureException.of(token.getSymbol());
            }
            String result = response.body().string();
            if (ObjectUtils.isEmpty(result)) {
                throw EthPriceRequestFailureException.of(token.getSymbol());
            }
            Map<String, Double> responseMap =  mapper.readValue(result, Map.class);
            Double ethPrice = responseMap.get("ETH");
            if (ethPrice == null) {
                throw EthPriceRequestFailureException.of(token.getSymbol());
            }
            return ethPrice;
        }
    }

    @CacheEvict(value = "token", allEntries = true)
    @Scheduled(fixedDelay = 300000)
    public void evictCache() {}

}
