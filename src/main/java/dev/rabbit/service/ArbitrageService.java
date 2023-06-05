package dev.rabbit.service;

import dev.rabbit.exception.EthPriceRequestFailureException;
import dev.rabbit.exception.GasEstimationFailureException;
import dev.rabbit.exception.RPCCallFailureException;
import dev.rabbit.exception.ZeroAmountException;
import dev.rabbit.pojo.Path;
import dev.rabbit.pool.Pool;
import dev.rabbit.pool.State;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

@Service
@AllArgsConstructor
@Slf4j
public class ArbitrageService {

    private final HttpService httpService;
    private final Web3j web3j;
    private final OkHttpClient okHttpClient;

    public static <T> List<List<T>> splitList(List<T> originalList, int chunkSize) {
        List<List<T>> subLists = new ArrayList<>();

        int listSize = originalList.size();
        for (int i = 0; i < listSize; i += chunkSize) {
            int end = Math.min(listSize, i + chunkSize);
            List<T> sublist = originalList.subList(i, end);
            subLists.add(sublist);
        }

        return subLists;
    }

    private static BigInteger getMaxBaseFeeInFutureBlock(BigInteger baseFee) {
        BigInteger maxBaseFee = new BigInteger(baseFee.toString());
        for (int i = 0; i < 2; i++) {
            maxBaseFee =
                    maxBaseFee.multiply(BigInteger.valueOf(1125)).divide(BigInteger.valueOf(1000)).add(ONE);
        }
        return maxBaseFee;
    }


    public List<Path> analyze(List<Path> paths) {
        getData(paths);
        System.gc();
        List<Path> pathsWithValueExtractable = paths.parallelStream().filter(Path::isValueExtractable).toList();
        if (pathsWithValueExtractable.isEmpty()) return Collections.emptyList();

        // Initialize State for parallel processing
        pathsWithValueExtractable.forEach(
                path -> path.getTrades().forEach(trade -> trade.getPool().getState().initialize()));

        List<Path> exploitablePaths = pathsWithValueExtractable.parallelStream().filter(path -> {
            try {
                path.calculateOptimalTradingVolume();
                path.getTrades().forEach(trade -> trade.getPool().getState().reset());
                path.populateInputAndOutputTradeAmountsWithTicks();
                path.calculateRevenue();
                path.encodeCallData();
                return true;
            }
            catch (ZeroAmountException | EthPriceRequestFailureException exception) {
                if (exception instanceof EthPriceRequestFailureException) {
                    System.out.println("FAILURE TO RETRIEVE ETH VALUE FOR TOKEN");
                    System.out.println(exception.getMessage());
                }
                return false;
            }
        }).sorted(Collections.reverseOrder()).toList();

        // Clear State after parallel processing
        pathsWithValueExtractable.forEach(
                path -> path.getTrades().forEach(trade -> trade.getPool().getState().clear()));

        if (exploitablePaths.isEmpty()) return Collections.emptyList();


        exploitablePaths = exploitablePaths.subList(0, Math.min(exploitablePaths.size(), 100));

        BigInteger baseFeePerGas = getBaseFeePerGas();
        BigInteger maxBaseFeeInFutureBlock = getMaxBaseFeeInFutureBlock(baseFeePerGas);
        AtomicBoolean profitable = new AtomicBoolean(false);

        List<Path> profitablePaths = exploitablePaths.parallelStream().filter((ePath) -> {
            if (profitable.get()) return false;
            ePath.setBaseFeePerGas(baseFeePerGas);
            ePath.setMaxBaseFeeInFutureBlock(maxBaseFeeInFutureBlock);
            try {
                ePath.simulate();
                if (ePath.getNetProfit().compareTo(ZERO) > 0) {
                    profitable.set(true);
                    return true;
                }
                return false;
            }
            catch (GasEstimationFailureException exception) {
                return false;
            }
        }).sorted(Collections.reverseOrder()).toList();
        if (profitablePaths.isEmpty()) return Collections.emptyList();

        profitablePaths.parallelStream().forEach(Path::buildSignedTransaction);

        for (Path profitablePath : profitablePaths) {
            BigDecimal profit = new BigDecimal(profitablePath.getNetProfit());
            System.out.println("Profit in ETH: " + profit.divide(BigDecimal.TEN.pow(18), MathContext.DECIMAL32));
        }
        return profitablePaths;
    }

    private void getData(List<Path> paths) {
        Set<Pool<? extends State>> allPoolsSet = new HashSet<>();
        paths.forEach(path -> {
            path.getTrades().forEach(trade -> {
                allPoolsSet.add(trade.getPool());
            });
        });
        List<Pool<? extends State>> allPools = new ArrayList<>(allPoolsSet);

        List<List<Pool<? extends State>>> allPoolsListSet = splitList(allPools, 2500);

        HttpService httpService1 = new HttpService("https://rabbit.dev/http", new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .readTimeout(10, TimeUnit.MINUTES)
                .build());

        for (List<Pool<? extends State>> pools : allPoolsListSet) {
            BatchRequest batchRequest = new BatchRequest(httpService1);

            for (int i = 0; i < pools.size(); i++) {
                List<Request<?, EthCall>> requests = pools.get(i).getRequestsForData();
                for (Request<?, EthCall> request : requests) {
                    request.setId(i);
                    batchRequest.add(request);
                }
            }

            try {
                List<? extends Response<?>> responses = batchRequest.send().getResponses();
                Map<Integer, List<Response<?>>> idToResponseMap = groupResponsesById(responses);
                for (Map.Entry<Integer, List<Response<?>>> entry : idToResponseMap.entrySet()) {
                    pools.get(entry.getKey()).assignData(entry.getValue());
                }
            }
            catch (IOException exception) {
                throw RPCCallFailureException.of("Failed to retrieve initial data for pools", exception);
            }
        }
        httpService1 = null;
    }

    private Map<Integer, List<Response<?>>> groupResponsesById(List<? extends Response<?>> responses) {
        Map<Integer, List<Response<?>>> idToResponseMap = new HashMap<>();
        for (Response<?> response : responses) {
            int id = (int) response.getId();
            List<Response<?>> idResponses = idToResponseMap.getOrDefault(id, new ArrayList<>());
            idResponses.add(response);
            idToResponseMap.put(id, idResponses);
        }
        return idToResponseMap;
    }

    private BigInteger getBaseFeePerGas() {
        try {
            return web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock()
                    .getBaseFeePerGas();
        }
        catch (IOException exception) {
            throw RPCCallFailureException.of("Failed to get the current block numbers base fee per gas", exception);
        }
    }
}
