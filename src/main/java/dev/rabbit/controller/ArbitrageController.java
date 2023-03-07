package dev.rabbit.controller;

import dev.rabbit.pojo.Body;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

import static dev.rabbit.pojo.Body.Message;

@RestController
public class ArbitrageController {
    @RequestMapping(value = "/", method = RequestMethod.POST)
    public ResponseEntity<String> receiveMessage(@RequestBody Body body) {
        // Get PubSub message from request body.
        Message message = body.getMessage();
        if (message == null) {
            String msg = "Bad Request: invalid Pub/Sub message format";
            System.out.println(msg);
            return new ResponseEntity<>(msg, HttpStatus.BAD_REQUEST);
        }
        String data = message.data();
        if (StringUtils.isBlank(data)) {
            String msg = "Bad Request: invalid Pub/Sub message format. Must contain data field.";
            System.out.println(msg);
            return new ResponseEntity<>(msg, HttpStatus.BAD_REQUEST);
        }
        String messageData = new String(Base64.getDecoder().decode(data));

        System.out.println(messageData);
        return new ResponseEntity<>(messageData, HttpStatus.OK);
    }
}
