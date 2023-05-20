package dev.rabbit.service;

import dev.rabbit.exception.RPCCallFailureException;
import dev.rabbit.exception.ZeroAmountException;
import dev.rabbit.factory.PoolFactory;
import dev.rabbit.pojo.Path;
import dev.rabbit.pool.Pool;
import dev.rabbit.pool.PoolState;
import dev.rabbit.spring.SpringContext;
import dev.rabbit.utils.ValueConverter;
import dev.rabbit.utils.Wallet;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.web3j.utils.Convert.fromWei;

@Service
@AllArgsConstructor
@Slf4j
public class ArbitrageService {

    private final HttpService httpService;
    private final ValueConverter valueConverter;
    private final Wallet wallet;

    private static BigInteger getMaxBaseFeeInFutureBlock(BigInteger baseFee, int blocksInFuture) {
        BigInteger maxBaseFee = new BigInteger(baseFee.toString());
        for (int i = 0; i < blocksInFuture; i++) {
            maxBaseFee =
                    maxBaseFee.multiply(BigInteger.valueOf(1125)).divide(BigInteger.valueOf(1000)).add(BigInteger.ONE);
        }
        return maxBaseFee;
    }

    public List<Path> analyze(List<Path> paths) {
        // Get all data for all involved pools.
        getData();

        // Filter out paths that are not exploitable.
        List<Path> pathsWithValueExtractable = paths.stream().filter(Path::isValueExtractable).toList();
        if (pathsWithValueExtractable.isEmpty()) return Collections.emptyList();

        // Run Math to calculate revenue and sort in descending order
        List<Path> exploitablePaths = pathsWithValueExtractable.stream().filter(path -> {
            try {
                path.calculateOptimalTradingVolume();
//                path.calculateRevenue(valueConverter);
//                path.encodeCallData();
                    return true;
            }
            catch (ZeroAmountException exception) {
                return false;
            }
        }).sorted(Collections.reverseOrder()).toList();
        if (exploitablePaths.isEmpty()) return Collections.emptyList();
//
//        int count = 0;
//        List<Path> profitablePaths = new ArrayList<>();
//        BigInteger baseFeePerGas =
//                web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getBaseFeePerGas();
//        BigInteger maxBaseFeeInFutureBlock = getMaxBaseFeeInFutureBlock(baseFeePerGas, 2);
//        for (Path path : exploitablePaths) {
//            path.setBaseFeePerGas(baseFeePerGas);
//            path.setMaxBaseFeeInFutureBlock(maxBaseFeeInFutureBlock);
//            if (count >= 5) break;
//            try {
//                path.simulate(web3j);
//                count++;
//                if (path.getNetProfitAfterBaseFee().compareTo(BigInteger.ZERO) <= 0) continue;
//                profitablePaths.add(path);
//                break;
//            }
//            catch (GasEstimationFailureException exception) {
//                // Ignore exception
//            }
//        }
//        if (profitablePaths.isEmpty()) return Collections.emptyList();
//
//        for (Path path : profitablePaths) {
//            path.buildSignedTransaction(web3j, wallet);
//        }
//
//        profitablePaths.sort(Collections.reverseOrder());
//        return profitablePaths;
        return exploitablePaths;
    }

    private void getData() {
        List<Pool<? extends PoolState>> allPools = new ArrayList<>(SpringContext.getBean(PoolFactory.class).getAllPools());
        BatchRequest batchRequest = new BatchRequest(httpService);

        for (int i = 0; i < allPools.size(); i++) {
            List<Request<?, EthCall>> requests = allPools.get(i).getRequestsForData();
            for (Request<?, EthCall> request : requests) {
                request.setId(i);
                batchRequest.add(request);
            }
        }

        try {
            List<? extends Response<?>> responses = batchRequest.send().getResponses();
            Map<Integer, List<Response<?>>> idToResponseMap = groupResponsesById(responses);
            for (Map.Entry<Integer, List<Response<?>>> entry : idToResponseMap.entrySet()) {
                allPools.get(entry.getKey()).assignData(entry.getValue());
            }
        } catch (IOException exception) {
            throw RPCCallFailureException.of("Failed to retrieve initial data for pools", exception);
        }
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
}
