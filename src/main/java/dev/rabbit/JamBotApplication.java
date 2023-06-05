package dev.rabbit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories
public class JamBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(JamBotApplication.class, args);
    }
}
