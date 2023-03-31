package dev.rabbit.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.exception.InvalidPubSubMessageException;
import dev.rabbit.gateway.PubSubOutboundGateway;
import dev.rabbit.pojo.Path;
import dev.rabbit.record.Body;
import dev.rabbit.record.Tree;
import dev.rabbit.service.ArbitrageService;
import dev.rabbit.service.BundleService;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static dev.rabbit.record.Body.PubSubMessage;

@RestController
@AllArgsConstructor
public class ArbitrageController {

    private final ObjectMapper mapper;
    private final ArbitrageService arbitrageService;
    private final BundleService bundleService;
    private final PubSubOutboundGateway pubSubOutboundGateway;

    @RequestMapping(value = "/", method = RequestMethod.POST)
    public ResponseEntity<Void> receiveMessage(@RequestBody Body body) throws IOException {
        verifyMessageFormat(body);
        List<Tree> trees = parseBody(body);
        List<Path> profitablePaths = arbitrageService.analyze(trees);
        if (profitablePaths.isEmpty()) return ResponseEntity.accepted().build();
        bundleService.sendBundles(profitablePaths);
        //Publish message
        Message<String> message = MessageBuilder.createMessage(mapper.writeValueAsString(profitablePaths.get(0)),
                new MessageHeaders(Collections.emptyMap()));
        pubSubOutboundGateway.sendToPubsub(message);
        return ResponseEntity.accepted().build();
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

    private List<Tree> parseBody(Body body) throws JsonProcessingException {
        String messageData = new String(Base64.getDecoder().decode(body.message().data()));
        JsonNode treeListNode = mapper.readTree(messageData).get("trees");
        return mapper.readValue(treeListNode.toString(), new TypeReference<>() {
        });
    }
}
