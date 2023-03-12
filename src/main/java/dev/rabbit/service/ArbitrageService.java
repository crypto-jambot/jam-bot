package dev.rabbit.service;

import dev.rabbit.exception.GetReservesFailureException;
import dev.rabbit.pojo.Path;
import dev.rabbit.record.Node;
import dev.rabbit.record.Reserves;
import dev.rabbit.record.Tree;
import lombok.AllArgsConstructor;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.rabbit.utils.Constants.NULL_ADDRESS;
import static dev.rabbit.utils.Constants.V2_GET_RESERVES_ENCODED_FUNCTION;
import static dev.rabbit.utils.Constants.V2_GET_RESERVES_FUNCTION;

@Service
@AllArgsConstructor
public class ArbitrageService {

    private final HttpService httpService;
    private final Web3j web3j;

    public void analyze(List<Tree> trees) {
        Map<String, Reserves> reservesMap = getAllReserves(trees);

        List<Path> paths = new ArrayList<>();

        trees.forEach(tree -> {
            Path path = Path.of();
            Node startToken = tree.node();
            tree.traverse(((start, end, pool, ordered) -> {
                path.addTrade(start, end, pool, ordered);
                if (end.equals(startToken)) paths.add(Path.of(path));
            }), () -> path.removeLast());
        });

        paths.forEach(System.out::println);
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
            Request<?, EthCall> request = web3j.ethCall(Transaction.createEthCallTransaction(NULL_ADDRESS, pool, V2_GET_RESERVES_ENCODED_FUNCTION), DefaultBlockParameterName.LATEST);
            request.setId(uniquePools.indexOf(pool));
            batchRequest.add(request);
        });

        try {
            batchRequest.send().getResponses().forEach(response -> {
                List<Type> type = FunctionReturnDecoder.decode((String) response.getResult(), V2_GET_RESERVES_FUNCTION.getOutputParameters());
                reservesMap.put(uniquePools.get((int) response.getId()), new Reserves((BigInteger) type.get(0).getValue(), (BigInteger) type.get(1).getValue()));
            });
            return reservesMap;
        } catch (IOException exception) {
            throw GetReservesFailureException.of(exception);
        }
    }
}
