package dev.rabbit.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.pojo.Body;
import dev.rabbit.pojo.Tree;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static dev.rabbit.pojo.Body.Message;

@RestController
public class ArbitrageController {

    private final ObjectMapper mapper;

    public ArbitrageController(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @RequestMapping(value = "/", method = RequestMethod.POST)
    public ResponseEntity<List<Tree>> receiveMessage(@RequestBody Body body) throws JsonProcessingException {
        // Get PubSub message from request body.
        Message message = body.message();
        if (message == null) {
            String msg = "Bad Request: invalid Pub/Sub message format";
            System.out.println(msg);
//            return new ResponseEntity<>(msg, HttpStatus.BAD_REQUEST);
        }
        String data = message.data();
        if (StringUtils.isBlank(data)) {
            String msg = "Bad Request: invalid Pub/Sub message format. Must contain data field.";
            System.out.println(msg);
//            return new ResponseEntity<>(msg, HttpStatus.BAD_REQUEST);
        }
        String messageData = new String(Base64.getDecoder().decode(data));
        JsonNode rootNode = mapper.readTree(messageData);
        JsonNode treeListNode = rootNode.get("trees");
        List<Tree> trees = mapper.readValue(treeListNode.toString(), new TypeReference<>() {});

        System.out.println(messageData);
        return new ResponseEntity<>(trees, HttpStatus.OK);
    }
}
