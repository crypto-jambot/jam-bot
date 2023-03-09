package dev.rabbit.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.exception.InvalidPubSubMessageException;
import dev.rabbit.pojo.Body;
import dev.rabbit.pojo.Tree;
import dev.rabbit.service.ArbitrageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;

import static dev.rabbit.pojo.Body.Message;

@RestController
public class ArbitrageController {

    private final ObjectMapper mapper;
    private final ArbitrageService arbitrageService;

    public ArbitrageController(ObjectMapper mapper, ArbitrageService arbitrageService) {
        this.mapper = mapper;
        this.arbitrageService = arbitrageService;
    }

    @RequestMapping(value = "/", method = RequestMethod.POST)
    public ResponseEntity<String> receiveMessage(@RequestBody Body body) throws JsonProcessingException {
        verifyMessageFormat(body);
        arbitrageService.analyze(parseBody(body));
        return new ResponseEntity<>("Ok big boi", HttpStatus.OK);
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
        return mapper.readValue(treeListNode.toString(), new TypeReference<>() {});
    }
}
