package dev.rabbit.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.exception.InvalidPubSubMessageException;
import dev.rabbit.exception.RPCCallFailureException;
import dev.rabbit.factory.PoolFactory;
import dev.rabbit.gateway.PubSubOutboundGateway;
import dev.rabbit.pojo.Path;
import dev.rabbit.pojo.Trees;
import dev.rabbit.record.Body;
import dev.rabbit.record.Tree;
import dev.rabbit.service.ArbitrageService;
import dev.rabbit.service.BundleService;
import dev.rabbit.service.MongoService;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.protocol.Web3j;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static dev.rabbit.record.Body.PubSubMessage;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.TWO;

@RestController
@AllArgsConstructor
public class ArbitrageController {

    private final ObjectMapper mapper;
    private final PoolFactory poolFactory;
    private final ArbitrageService arbitrageService;
    private final BundleService bundleService;
    private final PubSubOutboundGateway pubSubOutboundGateway;
    private final MongoService mongoService;
    private final Web3j web3j;


    @PostMapping(value = "/")
    public ResponseEntity<Void> receiveMessage(@RequestBody Body body) throws JsonProcessingException {
        verifyMessageFormat(body);
        try {
            Trees treesObj = parseBody(body);
            List<Path> profitablePaths = arbitrageService.analyze(derivePaths(treesObj.getTrees()));
            if (profitablePaths.isEmpty()) return ResponseEntity.accepted().build();
            BigInteger currentBlockNumber = getCurrentBlockNumber();
            bundleService.sendBundles(treesObj.getId(), currentBlockNumber, profitablePaths);
            for (Path path : profitablePaths) {
                Message<String> message = MessageBuilder.createMessage(mapper.writeValueAsString(path),
                        new MessageHeaders(Map.of("triggeringPool", treesObj.getId(), "targetBlock1", currentBlockNumber.add(ONE), "targetBlock2", currentBlockNumber.add(TWO))));
                pubSubOutboundGateway.sendToPubsub(message);
            }
            return ResponseEntity.accepted().build();
        }
        finally {
            poolFactory.clearPools();
        }
    }

    private void verifyMessageFormat(Body body) {
        PubSubMessage message = body.message();
        if (message == null) {
            throw InvalidPubSubMessageException.of("Must contain message field");
        }
        String data = message.data();
        if (StringUtils.isBlank(data)) {
            throw InvalidPubSubMessageException.of("Must contain data field");
        }
    }

    private Trees parseBody(Body body) throws JsonProcessingException {
        String messageData = new String(Base64.getDecoder().decode(body.message().data()));
        JsonNode root = mapper.readTree(messageData);
        return mongoService.pullTrees(root.get("id").asText(), root.get("ordered").asText());
    }

    private List<Path> derivePaths(List<Tree> trees) {
        // Traverse the list of trees to build a total list of paths.
        final List<Path> paths = new ArrayList<>();
        trees.forEach(tree -> {
            Path path = Path.of();
            Tree.Node startToken = tree.node();
            tree.traverse(((start, end, pool, ordered) -> {
                path.addTrade(start, end, poolFactory.createPool(pool), ordered);
                if (end.equals(startToken)) paths.add(Path.of(path));
            }), path::removeLast);
        });
        return paths;
    }

    private BigInteger getCurrentBlockNumber() {
        try {
            return web3j.ethBlockNumber().send().getBlockNumber();
        } catch (IOException exception) {
            throw RPCCallFailureException.of("Failure to get current block number", exception);
        }
    }
}
