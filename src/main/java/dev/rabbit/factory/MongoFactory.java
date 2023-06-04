package dev.rabbit.factory;

import com.mongodb.ConnectionString;
import dev.rabbit.config.MongoConfiguration;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoClientFactoryBean;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class MongoFactory {
    private MongoConfiguration mongoConfiguration;

    @Bean
    public MongoClientFactoryBean mongo() {
        MongoClientFactoryBean mongo = new MongoClientFactoryBean();
        ConnectionString connectionString = new ConnectionString(mongoConfiguration.getUrl());
        mongo.setConnectionString(connectionString);
        return mongo;
    }
}
