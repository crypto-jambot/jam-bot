package dev.rabbit.service;

import dev.rabbit.exception.GasEstimationFailureException;
import dev.rabbit.exception.GetReservesFailureException;
import dev.rabbit.exception.ZeroAmountOutException;
import dev.rabbit.gateway.PubSubOutboundGateway;
import dev.rabbit.pojo.Path;
import dev.rabbit.record.Node;
import dev.rabbit.record.Reserves;
import dev.rabbit.record.Tree;
import dev.rabbit.utils.ValueConverter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.rabbit.utils.Constants.NULL_ADDRESS;
import static dev.rabbit.utils.Constants.V2_GET_RESERVES_ENCODED_FN;
import static dev.rabbit.utils.Constants.V2_GET_RESERVES_FN;
import static org.web3j.utils.Convert.fromWei;
import static org.web3j.utils.Convert.Unit;

@Service
@AllArgsConstructor
@Slf4j
public class ArbitrageService {

    private final HttpService httpService;
    private final Web3j web3j;
    private final ValueConverter valueConverter;
    private final PubSubOutboundGateway pubSubOutboundGateway;

    public void analyze(List<Tree> trees) throws IOException {
        // Traverse the list of trees to build a total list of paths.
        final List<Path> paths = new ArrayList<>();
        trees.forEach(tree -> {
            Path path = Path.of();
            Node startToken = tree.node();
            tree.traverse(((start, end, pool, ordered) -> {
                path.addTrade(start, end, pool, ordered);
                if (end.equals(startToken)) paths.add(Path.of(path));
            }), path::removeLast);
        });

        // Get all reserves for all involved pools.
        Map<String, Reserves> reservesMap = getAllReserves(trees);

        // Add reserves to each path.
        paths.forEach(path -> path.getTrades().forEach(trade -> trade.getPool().addReserves(reservesMap)));

        // Filter out paths that are not exploitable.
        List<Path> pathsWithValueExtractable = paths.stream().filter(Path::isValueExtractable).toList();
        if (pathsWithValueExtractable.isEmpty()) return;

        // Run Math to calculate revenue and sort in descending order
        List<Path> exploitablePaths = pathsWithValueExtractable.stream().filter(path -> {
            try {
                path.calculateEquivalentLiquidity();
                path.calculateOptimalTradingVolume();
                path.calculateRevenue(valueConverter);
                path.encodeCallData();
                return true;
            } catch (ZeroAmountOutException | IOException exception) {
                return false;
            }
        }).sorted(Collections.reverseOrder()).toList();

        int count = 0;
        List<Path> profitablePaths = new ArrayList<>();
        BigInteger baseFeePerGas = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getBaseFeePerGas();
        BigInteger maxPriorityFeePerGas = web3j.ethMaxPriorityFeePerGas().send().getMaxPriorityFeePerGas().add(BigInteger.ONE);
        for (Path path : exploitablePaths) {
            path.setBaseFeePerGas(baseFeePerGas);
            path.setMaxPriorityFeePerGas(maxPriorityFeePerGas);
            if (count >= 5) break;
            try {
                path.simulate(web3j);
                count++;
                if (path.getNetProfit().compareTo(BigInteger.ZERO) <= 0) continue;
                profitablePaths.add(path);
            } catch (GasEstimationFailureException exception) {
                // Ignore exception
            }
        }
        profitablePaths.sort(Collections.reverseOrder());
        log.info(String.format("Found a path! Profit in ETH: %s Native token: %s", fromWei(profitablePaths.get(0).getNetProfit().toString(), Unit.ETHER).setScale(6, RoundingMode.HALF_DOWN), profitablePaths.get(0).getTrades().get(0).getStart().getSymbol()));

//        //Publish message
//        Message<String> message = MessageBuilder.createMessage(profitablePaths.get(0).prepareForFlashbots(), new MessageHeaders(Map.of("This", "Header boyyyy")));
//        pubSubOutboundGateway.sendToPubsub(message);
    }

    private List<String> getUniquePools(List<Tree> trees) {
        Set<String> uniquePools = new HashSet<>();
        trees.forEach(tree -> tree.traverse(swap -> swap.exchanges().forEach(exchange -> uniquePools.add(exchange.pool()))));
        return new ArrayList<>(uniquePools);
    }

    private Map<String, Reserves> getAllReserves(List<Tree> trees) {
        List<String> uniquePools = getUniquePools(trees);

        BatchRequest batchRequest = new BatchRequest(httpService);
        Map<String, Reserves> reservesMap = new HashMap<>();

        uniquePools.forEach(pool -> {
            Request<?, EthCall> request = web3j.ethCall(Transaction.createEthCallTransaction(NULL_ADDRESS, pool, V2_GET_RESERVES_ENCODED_FN), DefaultBlockParameterName.LATEST);
            request.setId(uniquePools.indexOf(pool));
            batchRequest.add(request);
        });

        try {
            batchRequest.send().getResponses().forEach(response -> {
                List<Type> type = FunctionReturnDecoder.decode((String) response.getResult(), V2_GET_RESERVES_FN.getOutputParameters());
                reservesMap.put(uniquePools.get((int) response.getId()), new Reserves((BigInteger) type.get(0).getValue(), (BigInteger) type.get(1).getValue()));
            });
            return reservesMap;
        } catch (IOException exception) {
            throw GetReservesFailureException.of(exception);
        }
    }
}
