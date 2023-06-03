package dev.rabbit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.exception.EthPriceRequestFailureException;
import dev.rabbit.exception.GasEstimationFailureException;
import dev.rabbit.exception.RPCCallFailureException;
import dev.rabbit.exception.ZeroAmountException;
import dev.rabbit.factory.PoolFactory;
import dev.rabbit.pojo.Path;
import dev.rabbit.pool.Pool;
import dev.rabbit.pool.State;
import dev.rabbit.spring.SpringContext;
import dev.rabbit.utils.ValueConverter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.math.BigInteger.ONE;

@Service
@AllArgsConstructor
@Slf4j
public class ArbitrageService {

    private final HttpService httpService;
    private final Web3j web3j;

    public List<Path> analyze(List<Path> paths) {
        getData();

        List<Path> pathsWithValueExtractable = paths.parallelStream().filter(Path::isValueExtractable).toList();
        if (pathsWithValueExtractable.isEmpty()) return Collections.emptyList();

        // Initialize State for parallel processing
        pathsWithValueExtractable.forEach(path -> path.getTrades().forEach(trade -> trade.getPool().getState().initialize()));

        System.out.println("Total value extractable paths: " + pathsWithValueExtractable.size());
        AtomicInteger countYe = new AtomicInteger(0);
        List<Path> exploitablePaths = pathsWithValueExtractable.parallelStream().filter(path -> {
            try {
                path.calculateOptimalTradingVolume();
                path.getTrades().forEach(trade -> trade.getPool().getState().reset());
                path.populateInputAndOutputTradeAmountsWithTicks();
                path.calculateRevenue();
                path.encodeCallData();
                return true;
            }
            catch (ZeroAmountException exception) {
                return false;
            } catch (EthPriceRequestFailureException exception) {
                log.error(ExceptionUtils.getStackTrace(exception));
                return false;
            } finally {
                System.out.println("Count so far: " + countYe.incrementAndGet() + " of " + pathsWithValueExtractable.size());
            }
        }).sorted(Collections.reverseOrder()).toList();

        // Clear State after parallel processing
        pathsWithValueExtractable.forEach(path -> path.getTrades().forEach(trade -> trade.getPool().getState().clear()));

        if (exploitablePaths.isEmpty()) return Collections.emptyList();

        exploitablePaths = exploitablePaths.subList(0, 300);

        BigInteger baseFeePerGas = getBaseFeePerGas();
        BigInteger maxBaseFeeInFutureBlock = getMaxBaseFeeInFutureBlock(baseFeePerGas);

        int count = 0;
        List<Path> profitablePaths = new ArrayList<>();
        for (Path path : exploitablePaths) {
            path.setBaseFeePerGas(baseFeePerGas);
            path.setMaxBaseFeeInFutureBlock(maxBaseFeeInFutureBlock);
            if (count >= 10) break;
            try {
                path.simulate();
//                if (path.getNetProfit().compareTo(BigInteger.ZERO) <= 0) continue;
                count++;
                System.out.println(count);
                profitablePaths.add(path);
            }
            catch (GasEstimationFailureException exception) {
                System.out.println(exception.getMessage());
            }
        }
        if (profitablePaths.isEmpty()) return Collections.emptyList();
        System.out.println("Total Errors: " + GasEstimationFailureException.totalErrors);

        GasEstimationFailureException.errors.forEach((key, value) -> System.out.printf("Error: %s | Occurrences: %d\n", key, value));

        profitablePaths.parallelStream().forEach(Path::buildSignedTransaction);

        System.out.println("Total Number of Paths: " + paths.size());
        System.out.println("Total Number of Valuable Extractable Paths: " + pathsWithValueExtractable.size());
        System.out.println("Total Number of Exploitable Paths: " + exploitablePaths.size());
        System.out.println("Total Number of Profitable Paths: " + profitablePaths.size());
        for (Path profitablePath : profitablePaths) {
            BigDecimal profit = new BigDecimal(profitablePath.getNetProfit());
            System.out.println("Profit in ETH: " + profit.divide(BigDecimal.TEN.pow(18), MathContext.DECIMAL32));
        }
        return profitablePaths;
    }

    private void getData() {
        List<Pool<? extends State>> allPools = new ArrayList<>(SpringContext.getBean(PoolFactory.class).getAllPools());
        List<List<Pool<? extends State>>> allPoolsListSet = splitList(allPools, 2500);
        List<List<Pool<? extends State>>> allPoolsListSet2 = splitList(allPools, 2000);

        for (List<Pool<? extends State>> pools : allPoolsListSet) {
            BatchRequest batchRequest = new BatchRequest(httpService);

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
            } catch (IOException exception) {
                throw RPCCallFailureException.of("Failed to retrieve initial data for pools", exception);
            }
        }

//        for (List<Pool<? extends State>> pools : allPoolsListSet2) {
//            BatchRequest batchRequest = new BatchRequest(httpService);
//
//            for (int i = 0; i < pools.size(); i++) {
//                List<Request<?, EthCall>> requests = pools.get(i).getSecondaryRequestsForData();
//                if (requests == null) continue;
//                for (Request<?, EthCall> request : requests) {
//                    request.setId(i);
//                    batchRequest.add(request);
//                }
//            }
//
//            if (batchRequest.getRequests().size() == 0) continue;
//            try {
//                List<? extends Response<?>> responses = batchRequest.send().getResponses();
//                Map<Integer, List<Response<?>>> idToResponseMap = groupResponsesById(responses);
//                for (Map.Entry<Integer, List<Response<?>>> entry : idToResponseMap.entrySet()) {
//                    pools.get(entry.getKey()).assignSecondaryData(entry.getValue());
//                }
//            } catch (IOException exception) {
//                throw RPCCallFailureException.of("Failed to retrieve initial data for pools", exception);
//            }
//        }
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
    private BigInteger getBaseFeePerGas() {
        try {
//            System.out.println(web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber());
            return web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getBaseFeePerGas();
        } catch (IOException exception) {
            throw RPCCallFailureException.of("Failed to get the current block numbers base fee per gas", exception);
        }
    }
}
