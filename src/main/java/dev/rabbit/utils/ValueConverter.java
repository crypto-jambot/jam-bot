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
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.rabbit.utils.Constants.WETH_ADDRESS;

@Component
@AllArgsConstructor
@Slf4j
public class ValueConverter {
    private static final String URL = "https://min-api.cryptocompare.com/data/price?fsym=%s&tsyms=ETH";
    private final OkHttpClient okHttpClient;
    private final ObjectMapper mapper;
    private final Map<String, Pair<Long, Double>> tokens = new ConcurrentHashMap<>();


    public BigDecimal deriveEtherValue(Double ethPrice, BigInteger profitBeforeGas) {
        return BigDecimal.valueOf(ethPrice).multiply(new BigDecimal(profitBeforeGas), MathContext.DECIMAL128);
    }

    public Double makeRequest(Token token) throws EthPriceRequestFailureException {
        if (token.getAddress().equalsIgnoreCase(WETH_ADDRESS)) return Double.valueOf("1.0");
        if (!tokens.containsKey(token.getAddress()) ||
                System.currentTimeMillis() - tokens.get(token.getAddress()).getFirst() >= 300000000L) {
            Request request = new Request.Builder()
                    .url(String.format(URL, token.getSymbol()))
                    .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (ObjectUtils.isEmpty(response) || ObjectUtils.isEmpty(response.body())) {
                    throw EthPriceRequestFailureException.of(token.getSymbol());
                }
                String result = response.body().string();
                if (ObjectUtils.isEmpty(result)) {
                    throw EthPriceRequestFailureException.of(token.getSymbol());
                }
                Map<String, Double> responseMap = mapper.readValue(result, Map.class);
                System.out.println(responseMap);
                Double ethPrice = responseMap.get("ETH");
                if (ethPrice == null) {
                    throw EthPriceRequestFailureException.of(token.getSymbol());
                }
                tokens.put(token.getAddress(), Pair.of(System.currentTimeMillis(), ethPrice));
                return ethPrice;
            }
            catch (IOException e) {
                throw EthPriceRequestFailureException.of(token.getSymbol());
            }
        }
        return tokens.get(token.getAddress()).getSecond();
    }

    @CacheEvict(value = "token", allEntries = true)
    @Scheduled(fixedDelay = 300000)
    public void evictCache() {
    }

}
