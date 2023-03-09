package dev.rabbit.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.exception.InvalidPubSubMessageException;
import dev.rabbit.record.Body;
import dev.rabbit.record.Tree;
import dev.rabbit.service.ArbitrageService;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;

import static dev.rabbit.record.Body.Message;

@RestController
@AllArgsConstructor
public class ArbitrageController {

    private final ObjectMapper mapper;
    private final ArbitrageService arbitrageService;

    @RequestMapping(value = "/", method = RequestMethod.POST)
    public ResponseEntity<List<Tree>> receiveMessage(@RequestBody Body body) throws JsonProcessingException {
        verifyMessageFormat(body);
        List<Tree> trees = parseBody(body);
        arbitrageService.analyze(trees);
        return new ResponseEntity<>(trees, HttpStatus.OK);
    }

    private void verifyMessageFormat(Body body) {
        Message message = body.message();
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
